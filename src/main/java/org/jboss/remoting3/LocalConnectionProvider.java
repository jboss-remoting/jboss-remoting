/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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
import java.util.concurrent.Executor;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.xnio.Cancellable;
import org.xnio.OptionMap;
import org.xnio.Result;

import javax.security.auth.callback.CallbackHandler;

import static org.xnio.IoUtils.nullCancellable;

final class LocalConnectionProvider implements ConnectionProvider {

    private final Executor executor;
    private final ConnectionProviderContext context;
    private static final Object providerInterface = new Object();

    LocalConnectionProvider(final ConnectionProviderContext context, final Executor executor) {
        this.context = context;
        this.executor = executor;
    }

    public Cancellable connect(final URI uri, final OptionMap connectOptions, final Result<ConnectionHandlerFactory> result, final CallbackHandler callbackHandler) throws IllegalArgumentException {
        context.accept(new ConnectionHandlerFactory() {
            public ConnectionHandler createInstance(final ConnectionHandlerContext connectionContext) {
                return new LoopbackConnectionHandler(connectionContext);
            }
        });
        return nullCancellable();
    }

    public Object getProviderInterface() {
        return providerInterface;
    }

    private class LoopbackConnectionHandler implements ConnectionHandler {

        private final ConnectionHandlerContext context;

        LoopbackConnectionHandler(final ConnectionHandlerContext context) {
            this.context = context;
        }

        public Cancellable open(final String serviceType, final Result<Channel> result, final OptionMap optionMap) {
            LoopbackChannel channel = new LoopbackChannel(executor);
            try {
                context.openService(channel.getOtherSide(), serviceType);
            } catch (ServiceNotFoundException e) {
                result.setException(e);
                return nullCancellable();
            }
            result.setResult(channel);
            return nullCancellable();
        }

        public void close() throws IOException {
        }
    }
}
