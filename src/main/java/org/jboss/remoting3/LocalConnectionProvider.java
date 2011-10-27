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

import java.net.URI;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.SpiUtils;
import org.xnio.Cancellable;
import org.xnio.OptionMap;
import org.xnio.Result;

import javax.security.auth.callback.CallbackHandler;

import static org.xnio.IoUtils.nullCancellable;

final class LocalConnectionProvider extends AbstractHandleableCloseable<ConnectionProvider> implements ConnectionProvider {

    private final Executor executor;
    private final ConnectionProviderContext context;

    LocalConnectionProvider(final ConnectionProviderContext context, final Executor executor) {
        super(executor);
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
        return NO_PROVIDER_INTERFACES;
    }

    private class LoopbackConnectionHandler extends AbstractHandleableCloseable<ConnectionHandler> implements ConnectionHandler {

        private final ConnectionHandlerContext context;

        LoopbackConnectionHandler(final ConnectionHandlerContext context) {
            super(executor);
            this.context = context;
        }

        public Cancellable open(final String serviceType, final Result<Channel> result, final OptionMap optionMap) {
            LocalChannel channel = new LocalChannel(executor, context);
            try {
                final OpenListener openListener = context.getServiceOpenListener(serviceType);
                if (openListener == null) {
                    throw new ServiceNotFoundException("Unable to find service type '" + serviceType + "'");
                }
                context.getConnectionProviderContext().getExecutor().execute(SpiUtils.getServiceOpenTask(channel.getOtherSide(), openListener));
            } catch (ServiceNotFoundException e) {
                result.setException(e);
                return nullCancellable();
            }
            result.setResult(channel);
            return nullCancellable();
        }

        public Collection<Principal> getPrincipals() {
            return Collections.emptySet();
        }

        public String getRemoteEndpointName() {
            return context.getConnectionProviderContext().getEndpoint().getName();
        }
    }

    public String toString() {
        return String.format("Remoting local connection provider %x for %s", Integer.valueOf(hashCode()), context.getEndpoint());
    }
}
