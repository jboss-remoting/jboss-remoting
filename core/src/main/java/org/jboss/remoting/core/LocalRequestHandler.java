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
import java.util.concurrent.RejectedExecutionException;
import org.jboss.remoting.RemoteExecutionException;
import org.jboss.remoting.RequestListener;
import org.jboss.remoting.RemoteRequestException;
import org.jboss.remoting.spi.AbstractAutoCloseable;
import org.jboss.remoting.spi.RemoteRequestContext;
import org.jboss.remoting.spi.ReplyHandler;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.SpiUtils;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class LocalRequestHandler<I, O> extends AbstractAutoCloseable<RequestHandler> implements RequestHandler {

    private final RequestListener<I, O> requestListener;
    private final ClientContextImpl clientContext;
    private final Class<I> requestClass;
    private final Class<O> replyClass;

    private static final Logger log = Logger.getLogger("org.jboss.remoting.listener");

    LocalRequestHandler(Config<I, O> config) {
        super(config.getExecutor());
        requestListener = config.getRequestListener();
        clientContext = config.getClientContext();
        requestClass = config.getRequestClass();
        replyClass = config.getReplyClass();
    }

    public RemoteRequestContext receiveRequest(final Object request, final ReplyHandler replyHandler) {
        final RequestContextImpl<O> context = new RequestContextImpl<O>(replyHandler, clientContext, replyClass);
        try {
            final I castRequest;
            try {
                castRequest = requestClass.cast(request);
            } catch (ClassCastException e) {
                SpiUtils.safeHandleException(replyHandler, new RemoteRequestException("Request is the wrong type; expected " + requestClass + " but got " + request.getClass()));
                return SpiUtils.getBlankRemoteRequestContext();
            }
            context.execute(new Runnable() {
                public void run() {
                    try {
                        requestListener.handleRequest(context, castRequest);
                    } catch (RemoteExecutionException e) {
                        SpiUtils.safeHandleException(replyHandler, e);
                    } catch (Throwable t) {
                        SpiUtils.safeHandleException(replyHandler, new RemoteExecutionException("Request handler threw an exception", t));
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            SpiUtils.safeHandleException(replyHandler, new RemoteRequestException("Execution was rejected (server may be too busy)", e));
            return SpiUtils.getBlankRemoteRequestContext();
        }
        return new RemoteRequestContext() {
            public void cancel() {
                context.cancel();
            }
        };
    }

    protected void closeAction() throws IOException {
        try {
            requestListener.handleClientClose(clientContext);
        } catch (Throwable t) {
            log.error(t, "Unexpected exception in request listener client close handler method");
        }
    }

    void open() throws IOException {
        try {
            requestListener.handleClientOpen(clientContext);
        } catch (Throwable t) {
            final IOException ioe = new IOException("Failed to open client context");
            ioe.initCause(t);
            throw ioe;
        }
    }

    public String toString() {
        return "local request handler <" + Integer.toHexString(hashCode()) + "> (request listener = " + String.valueOf(requestListener) + ")";
    }

    static class Config<I, O> {
        private final Class<I> requestClass;
        private final Class<O> replyClass;

        private Executor executor;
        private RequestListener<I, O> requestListener;
        private ClientContextImpl clientContext;

        Config(final Class<I> requestClass, final Class<O> replyClass) {
            this.requestClass = requestClass;
            this.replyClass = replyClass;
        }

        public Class<I> getRequestClass() {
            return requestClass;
        }

        public Class<O> getReplyClass() {
            return replyClass;
        }

        public Executor getExecutor() {
            return executor;
        }

        public void setExecutor(final Executor executor) {
            this.executor = executor;
        }

        public RequestListener<I, O> getRequestListener() {
            return requestListener;
        }

        public void setRequestListener(final RequestListener<I, O> requestListener) {
            this.requestListener = requestListener;
        }

        public ClientContextImpl getClientContext() {
            return clientContext;
        }

        public void setClientContext(final ClientContextImpl clientContext) {
            this.clientContext = clientContext;
        }
    }
}
