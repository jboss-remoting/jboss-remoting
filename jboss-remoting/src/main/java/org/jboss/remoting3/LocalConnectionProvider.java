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

import java.net.URI;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Result;

import javax.security.auth.callback.CallbackHandler;

final class LocalConnectionProvider implements ConnectionProvider {
    private final ConnectionProviderContext providerContext;

    public LocalConnectionProvider(final ConnectionProviderContext providerContext) {
        this.providerContext = providerContext;
    }

    public Cancellable connect(final URI uri, final OptionMap connectOptions, final Result<ConnectionHandlerFactory> result, final CallbackHandler callbackHandler) throws IllegalArgumentException {
        result.setResult(new ConnectionHandlerFactory() {
            public ConnectionHandler createInstance(final ConnectionHandlerContext outboundContext) {
                final Holder h = new Holder();
                providerContext.accept(new ConnectionHandlerFactory() {
                    public ConnectionHandler createInstance(final ConnectionHandlerContext inboundContext) {
                        final LocalConnectionHandler inboundHandler = new LocalConnectionHandler(inboundContext, connectOptions);
                        h.set(inboundHandler);
                        return new LocalConnectionHandler(outboundContext, connectOptions);
                    }
                });
                return h.get(); // outbound connection handler
            }
        });
        return IoUtils.nullCancellable();
    }

    public Void getProviderInterface() {
        return null;
    }

    private static final class Holder {
        private ConnectionHandler handler;

        ConnectionHandler get() {
            boolean intr = false;
            try {
                synchronized (this) {
                    ConnectionHandler handler;
                    while ((handler = this.handler) == null) try {
                        wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                    return handler;
                }
            } finally {
                if (intr) Thread.currentThread().interrupt();
            }
        }

        void set(ConnectionHandler handler) {
            synchronized (this) {
                this.handler = handler;
                notifyAll();
            }
        }
    }
}
