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

package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.remote.RemoteClientEndpoint;
import org.jboss.cx.remoting.spi.remote.RemoteRequestContext;
import org.jboss.cx.remoting.spi.remote.ReplyHandler;
import org.jboss.cx.remoting.spi.SpiUtils;
import org.jboss.cx.remoting.spi.AbstractAutoCloseable;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.xnio.log.Logger;
import java.util.concurrent.Executor;

/**
 *
 */
public final class RemoteClientEndpointLocalImpl<I, O> extends AbstractAutoCloseable<RemoteClientEndpoint> implements RemoteClientEndpoint {

    private final RequestListener<I, O> requestListener;
    private final Executor executor;
    private final ClientContextImpl clientContext;

    private static final Logger log = Logger.getLogger(RemoteClientEndpointLocalImpl.class);

    private RemoteClientEndpointLocalImpl(final Executor executor, final RequestListener<I, O> requestListener, final ClientContextImpl clientContext) {
        super(executor);
        this.executor = executor;
        this.requestListener = requestListener;
        this.clientContext = clientContext;
    }

    RemoteClientEndpointLocalImpl(final Executor executor, final RemoteServiceEndpointLocalImpl<I, O> service, final RequestListener<I, O> requestListener) {
        this(executor, requestListener, new ClientContextImpl(service.getServiceContext()));
    }

    RemoteClientEndpointLocalImpl(final Executor executor, final RequestListener<I, O> requestListener) {
        this(executor, requestListener, new ClientContextImpl(executor));
    }

    public void receiveRequest(final Object request) {
        final RequestContextImpl<O> context = new RequestContextImpl<O>(clientContext);
        executor.execute(new Runnable() {
            @SuppressWarnings({ "unchecked" })
            public void run() {
                try {
                    requestListener.handleRequest(context, (I) request);
                } catch (Throwable t) {
                    log.error(t, "Unexpected exception in request listener");
                }
            }
        });
    }

    public RemoteRequestContext receiveRequest(final Object request, final ReplyHandler replyHandler) {
        final RequestContextImpl<O> context = new RequestContextImpl<O>(replyHandler, clientContext);
        executor.execute(new Runnable() {
            @SuppressWarnings({ "unchecked" })
            public void run() {
                try {
                    requestListener.handleRequest(context, (I) request);
                } catch (RemoteExecutionException e) {
                    SpiUtils.safeHandleException(replyHandler, e.getMessage(), e.getCause());
                } catch (Throwable t) {
                    SpiUtils.safeHandleException(replyHandler, "Unexpected exception in request listener", t);
                }
            }
        });
        return new RemoteRequestContext() {
            public void cancel(final boolean mayInterrupt) {
                context.cancel(mayInterrupt);
            }
        };
    }

    void open() throws RemotingException {
        try {
            requestListener.handleClientOpen(clientContext);
            addCloseHandler(new CloseHandler<RemoteClientEndpoint>() {
                public void handleClose(final RemoteClientEndpoint closed) {
                    try {
                        requestListener.handleClientClose(clientContext);
                    } catch (Throwable t) {
                        log.error(t, "Unexpected exception in request listener client close handler method");
                    }
                }
            });
        } catch (Throwable t) {
            throw new RemotingException("Failed to open client context", t);
        }
    }

    public String toString() {
        return "local client endpoint (request listener = " + String.valueOf(requestListener) + ")";
    }
}
