/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3;

import java.io.IOException;
import java.net.URI;
import org.jboss.remoting3.security.RemotingPermission;
import org.jboss.remoting3.service.Services;
import org.jboss.remoting3.service.locator.ServiceReply;
import org.jboss.remoting3.service.locator.ServiceRequest;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.RequestHandlerConnector;
import org.jboss.xnio.FailedIoFuture;
import org.jboss.xnio.FutureResult;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.TranslatingResult;

class ConnectionImpl extends AbstractHandleableCloseable<Connection> implements Connection {

    private final Attachments attachments = new AttachmentsImpl();

    private final EndpointImpl endpoint;
    private final ConnectionHandler connectionHandler;
    private final IoFuture<? extends Client<ServiceRequest, ServiceReply>> serviceLocator;
    private final String name;

    ConnectionImpl(final EndpointImpl endpoint, final ConnectionHandlerFactory connectionHandlerFactory, final ConnectionProviderContext connectionProviderContext, final String name) {
        super(endpoint.getExecutor());
        this.endpoint = endpoint;
        this.name = name;
        connectionHandler = connectionHandlerFactory.createInstance(endpoint.new LocalConnectionContext(connectionProviderContext, this));
        serviceLocator = doOpenClient(Services.LOCATE_SERVICE, ServiceRequest.class, ServiceReply.class);
    }

    protected void closeAction() throws IOException {
        connectionHandler.close();
    }

    public <I, O> IoFuture<? extends Client<I, O>> openClient(final int serviceSlot, final Class<I> requestClass, final Class<O> replyClass) {
        return openClient(serviceSlot, requestClass, replyClass, OptionMap.EMPTY);
    }

    public <I, O> IoFuture<? extends Client<I, O>> openClient(final int serviceSlot, final Class<I> requestClass, final Class<O> replyClass, final OptionMap optionMap) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RemotingPermission("openClientByID"));
        }
        return doOpenClient(serviceSlot, requestClass, replyClass);
    }

    private <I, O> IoFuture<? extends Client<I, O>> doOpenClient(final int serviceSlot, final Class<I> requestClass, final Class<O> replyClass) {
        final FutureResult<Client<I, O>> futureResult = new FutureResult<Client<I, O>>();
        futureResult.addCancelHandler(connectionHandler.open(serviceSlot, new ClientWrapper<I, O>(endpoint, futureResult, requestClass, replyClass)));
        return futureResult.getIoFuture();
    }

    public <I, O> IoFuture<? extends Client<I, O>> openClient(final String serviceType, final String groupName, final Class<I> requestClass, final Class<O> replyClass) {
        return openClient(serviceType, groupName, requestClass, replyClass, OptionMap.EMPTY);
    }

    public <I, O> IoFuture<? extends Client<I, O>> openClient(final String serviceType, final String groupName, final Class<I> requestClass, final Class<O> replyClass, final OptionMap optionMap) {
        final Client<ServiceRequest, ServiceReply> locator;
        try {
            locator = serviceLocator.get();
        } catch (IOException e) {
            return new FailedIoFuture<Client<I, O>>(e);
        }
        final FutureResult<Client<I, O>> futureClientResult = new FutureResult<Client<I, O>>();
        try {
            final IoFuture<? extends ServiceReply<I, O>> futureServiceReply = locator.sendTyped(new ServiceRequest<I, O>(serviceType, groupName, requestClass, replyClass));
            futureServiceReply.addNotifier(new ServiceReplyNotifier<I, O>(requestClass, replyClass), futureClientResult);
            futureClientResult.addCancelHandler(futureServiceReply);
        } catch (IOException e) {
            return new FailedIoFuture<Client<I, O>>(e);
        }
        return futureClientResult.getIoFuture();
    }

    public <I, O> ClientConnector<I, O> createClientConnector(final RequestListener<I, O> listener, final Class<I> requestClass, final Class<O> replyClass) throws IOException {
        return createClientConnector(listener, requestClass, replyClass, OptionMap.EMPTY);
    }

    public <I, O> ClientConnector<I, O> createClientConnector(final RequestListener<I, O> listener, final Class<I> requestClass, final Class<O> replyClass, final OptionMap optionMap) throws IOException {
        final RequestHandler localRequestHandler = endpoint.createLocalRequestHandler(listener, requestClass, replyClass);
        final RequestHandlerConnector connector = connectionHandler.createConnector(localRequestHandler);
        final ClientContextImpl context = new ClientContextImpl(getExecutor(), this);
        context.addCloseHandler(new CloseHandler<ClientContext>() {
            public void handleClose(final ClientContext closed) {
                IoUtils.safeClose(localRequestHandler);
            }
        });
        return new ClientConnectorImpl<I, O>(connector, endpoint, requestClass, replyClass, context);
    }

    public Attachments getAttachments() {
        return attachments;
    }

    public String toString() {
        return "Connection to " + name;
    }

    private static class ClientWrapper<I, O> extends TranslatingResult<RequestHandler, Client<I, O>> {

        private final Class<I> requestClass;
        private final Class<O> replyClass;
        private EndpointImpl endpoint;

        public ClientWrapper(final EndpointImpl endpoint, final FutureResult<Client<I, O>> futureResult, final Class<I> requestClass, final Class<O> replyClass) {
            super(futureResult);
            this.requestClass = requestClass;
            this.replyClass = replyClass;
            this.endpoint = endpoint;
        }

        protected Client<I, O> translate(final RequestHandler input) throws IOException {
            return endpoint.createClient(input, requestClass, replyClass);
        }
    }

    private class ServiceReplyNotifier<I, O> extends IoFuture.HandlingNotifier<ServiceReply<I, O>, FutureResult<Client<I, O>>> {
        private final Class<?> expectedRequestClass;
        private final Class<?> expectedReplyClass;

        private ServiceReplyNotifier(final Class<?> expectedRequestClass, final Class<?> expectedReplyClass) {
            this.expectedRequestClass = expectedRequestClass;
            this.expectedReplyClass = expectedReplyClass;
        }

        public void handleCancelled(final FutureResult<Client<I, O>> attachment) {
            attachment.setCancelled();
        }

        public void handleFailed(final IOException exception, final FutureResult<Client<I, O>> attachment) {
            if (exception instanceof RemoteExecutionException) {
                final Throwable cause = exception.getCause();
                if (cause instanceof IOException) {
                    attachment.setException((IOException) cause);
                    return;
                }
            }
            attachment.setException(exception);
        }

        public void handleDone(final ServiceReply<I, O> data, final FutureResult<Client<I, O>> attachment) {
            final Class<I> actualRequestClass = data.getActualRequestClass();
            final Class<O> actualReplyClass = data.getActualReplyClass();
            try {
                actualRequestClass.asSubclass(expectedRequestClass);
                expectedReplyClass.asSubclass(actualReplyClass);
            } catch (ClassCastException e) {
                attachment.setException(new ServiceOpenException("Incorrect type specified", e));
                return;
            }
            final IoFuture<? extends Client<I, O>> futureClient = doOpenClient(data.getSlot(), actualRequestClass, actualReplyClass);
            futureClient.addNotifier(IoUtils.<Client<I, O>>resultNotifier(), attachment);
            attachment.addCancelHandler(futureClient);
        }
    }
}
