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

package org.jboss.remoting3;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.jboss.remoting3.spi.AbstractAutoCloseable;
import org.jboss.remoting3.spi.RemoteRequestContext;
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.SpiUtils;
import org.jboss.xnio.log.Logger;

/**
 *
 */
final class LocalRequestHandler<I, O> extends AbstractAutoCloseable<RequestHandler> implements RequestHandler {

    private final RequestListener<I, O> requestListener;
    private final ClientContextImpl clientContext;
    private final Class<I> requestClass;
    private final Class<O> replyClass;

    private static final Logger log = Logger.getLogger("org.jboss.remoting.listener");

    LocalRequestHandler(final Executor executor, final RequestListener<I, O> requestListener, final ClientContextImpl clientContext, final Class<I> requestClass, final Class<O> replyClass) {
        super(executor);
        this.requestListener = requestListener;
        this.clientContext = clientContext;
        this.requestClass = requestClass;
        this.replyClass = replyClass;
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
            requestListener.handleClose();
        } catch (Throwable t) {
            log.error(t, "Unexpected exception in request listener handleClose() method");
        }
    }

    public String toString() {
        return "local request handler <" + Integer.toHexString(hashCode()) + "> (request listener = " + String.valueOf(requestListener) + ")";
    }
}
