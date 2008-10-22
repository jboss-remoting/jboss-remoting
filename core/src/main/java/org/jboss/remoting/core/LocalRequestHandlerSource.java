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

import org.jboss.remoting.spi.remote.RequestHandlerSource;
import org.jboss.remoting.spi.remote.RequestHandler;
import org.jboss.remoting.spi.remote.Handle;
import org.jboss.remoting.spi.AbstractAutoCloseable;
import org.jboss.remoting.RequestListener;
import org.jboss.remoting.CloseHandler;
import org.jboss.xnio.log.Logger;
import java.util.concurrent.Executor;
import java.io.IOException;

/**
 *
 */
public final class LocalRequestHandlerSource<I, O> extends AbstractAutoCloseable<RequestHandlerSource> implements RequestHandlerSource {

    private final RequestListener<I, O> requestListener;
    private final ServiceContextImpl serviceContext;
    private final Executor executor;

    private static final Logger log = Logger.getLogger(LocalRequestHandlerSource.class);

    LocalRequestHandlerSource(final Executor executor, final RequestListener<I, O> requestListener) {
        super(executor);
        this.requestListener = requestListener;
        this.executor = executor;
        serviceContext = new ServiceContextImpl(executor);
    }

    public Handle<RequestHandler> createRequestHandler() throws IOException {
        if (isOpen()) {
            final LocalRequestHandler<I, O> localRequestHandler = new LocalRequestHandler<I, O>(executor, this, requestListener);
            localRequestHandler.open();
            return localRequestHandler.getHandle();
        } else {
            throw new IOException("LocalRequestHandlerSource is closed");
        }
    }

    void open() throws IOException {
        try {
            requestListener.handleServiceOpen(serviceContext);
            addCloseHandler(new CloseHandler<RequestHandlerSource>() {
                public void handleClose(final RequestHandlerSource closed) {
                    try {
                        requestListener.handleServiceClose(serviceContext);
                    } catch (Throwable t) {
                        log.error(t, "Unexpected exception in request listener client close handler method");
                    }
                }
            });
        } catch (Throwable t) {
            final IOException ioe = new IOException("Failed to open client context");
            ioe.initCause(t);
            throw ioe;
        }
    }

    ServiceContextImpl getServiceContext() {
        return serviceContext;
    }

    public String toString() {
        return "local request handler source <" + Integer.toString(hashCode(), 16) + "> (request listener = " + String.valueOf(requestListener) + ")";
    }
}
