/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting.core;

import java.io.IOException;
import java.util.concurrent.Executor;
import org.jboss.remoting.Client;
import org.jboss.remoting.IndeterminateOutcomeException;
import org.jboss.remoting.RemoteRequestException;
import org.jboss.remoting.CloseHandler;
import org.jboss.remoting.core.util.QueueExecutor;
import org.jboss.remoting.spi.Handle;
import org.jboss.remoting.spi.RemoteRequestContext;
import org.jboss.remoting.spi.ReplyHandler;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class ClientImpl<I, O> extends AbstractContextImpl<Client<I, O>> implements Client<I, O> {

    private static final Logger log = Logger.getLogger("org.jboss.remoting.client");

    private final Handle<RequestHandler> handle;
    private final Class<I> requestClass;
    private final Class<O> replyClass;

    private ClientImpl(final Handle<RequestHandler> handle, final Executor executor, final Class<I> requestClass, final Class<O> replyClass) {
        super(executor);
        this.handle = handle;
        this.requestClass = requestClass;
        this.replyClass = replyClass;
    }

    static <I, O> ClientImpl<I, O> create(final Handle<RequestHandler> handle, final Executor executor, final Class<I> requestClass, final Class<O> replyClass) {
        final ClientImpl<I, O> ci = new ClientImpl<I, O>(handle, executor, requestClass, replyClass);
        handle.addCloseHandler(new CloseHandler<Handle<RequestHandler>>() {
            public void handleClose(final Handle<RequestHandler> closed) {
                IoUtils.safeClose(ci);
            }
        });
        return ci;
    }

    protected void closeAction() throws IOException {
        handle.close();
    }

    public O invoke(final I request) throws IOException {
        if (! isOpen()) {
            throw new IOException("Client is not open");
        }
        final I actualRequest = castRequest(request);
        final QueueExecutor executor = new QueueExecutor();
        final FutureReplyImpl<O> futureReply = new FutureReplyImpl<O>(executor, replyClass);
        final ReplyHandler replyHandler = futureReply.getReplyHandler();
        final RemoteRequestContext requestContext = handle.getResource().receiveRequest(actualRequest, replyHandler);
        futureReply.setRemoteRequestContext(requestContext);
        futureReply.addNotifier(IoUtils.<O>attachmentClosingNotifier(), executor);
        executor.runQueue();
        try {
            return futureReply.getInterruptibly();
        } catch (InterruptedException e) {
            try {
                futureReply.cancel();
                throw new IndeterminateOutcomeException("The current thread was interrupted before the result could be read");
            } finally {
                Thread.currentThread().interrupt();
            }
        }
    }

    public IoFuture<O> send(final I request) throws IOException {
        if (! isOpen()) {
            throw new IOException("Client is not open");
        }
        final I actualRequest = castRequest(request);
        final FutureReplyImpl<O> futureReply = new FutureReplyImpl<O>(executor, replyClass);
        final ReplyHandler replyHandler = futureReply.getReplyHandler();
        final RemoteRequestContext requestContext = handle.getResource().receiveRequest(actualRequest, replyHandler);
        futureReply.setRemoteRequestContext(requestContext);
        return futureReply;
    }

    /**
     * Since type is erased, it's possible that the wrong type was passed.
     * @param request
     * @return
     * @throws RemoteRequestException
     */
    private I castRequest(final Object request) throws RemoteRequestException {
        try {
            return requestClass.cast(request);
        } catch (ClassCastException e) {
            throw new RemoteRequestException("Invalid request type sent (got <" + request.getClass().getName() + ">, expected <? extends " + requestClass.getName() + ">");
        }
    }

    public String toString() {
        return "client instance <" + Integer.toHexString(hashCode()) + ">";
    }

    Handle<RequestHandler> getRequestHandlerHandle() {
        return handle;
    }

    Class<I> getRequestClass() {
        return requestClass;
    }

    Class<O> getReplyClass() {
        return replyClass;
    }
}
