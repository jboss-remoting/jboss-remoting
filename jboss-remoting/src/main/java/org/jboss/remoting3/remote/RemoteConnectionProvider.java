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

package org.jboss.remoting3.remote;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.jboss.marshalling.ProviderDescriptor;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.security.ServerAuthenticationProvider;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.jboss.remoting3.spi.ProtocolServiceType;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.Connector;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Result;
import org.jboss.xnio.channels.ConnectedStreamChannel;

import javax.security.auth.callback.CallbackHandler;

/**
 * The connection provider class for the "remote" protocol.
 */
final class RemoteConnectionProvider implements ConnectionProvider {

    private final ConnectionProviderContext connectionProviderContext;
    private final Connector<InetSocketAddress, ? extends ConnectedStreamChannel<InetSocketAddress>> connector;
    private final ProviderInterface providerInterface = new ProviderInterface();
    private final ProviderDescriptor providerDescriptor;

    RemoteConnectionProvider(final ConnectionProviderContext connectionProviderContext, final Connector<InetSocketAddress, ? extends ConnectedStreamChannel<InetSocketAddress>> connector) {
        this.connectionProviderContext = connectionProviderContext;
        this.connector = connector;
        final ProviderDescriptor providerDescriptor = connectionProviderContext.getProtocolServiceProvider(ProtocolServiceType.MARSHALLER_PROVIDER_DESCRIPTOR, "river");
        if (providerDescriptor == null) {
            throw new IllegalArgumentException("River marshalling protocol is not installed");
        }
        this.providerDescriptor = providerDescriptor;
    }

    public Cancellable connect(final URI uri, final OptionMap connectOptions, final Result<ConnectionHandlerFactory> result, final CallbackHandler callbackHandler) throws IllegalArgumentException {
        // Get the destination info from the URI
        final String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("No host name specified");
        }
        final int port = uri.getPort();
        if (port < 1) {
            throw new IllegalArgumentException("Port number must be specified");
        }
        // Open a client channel
        final IoFuture<? extends ConnectedStreamChannel<InetSocketAddress>> futureChannel;
        try {
            futureChannel = connector.connectTo(new InetSocketAddress(InetAddress.getByName(host), port), new ClientOpenListener(connectOptions, connectionProviderContext, result, callbackHandler, providerDescriptor), null);
        } catch (UnknownHostException e) {
            result.setException(e);
            return IoUtils.nullCancellable();
        }
        return futureChannel;
    }

    public NetworkServerProvider getProviderInterface() {
        return providerInterface;
    }

    private class ProviderInterface implements NetworkServerProvider {
        public ChannelListener<ConnectedStreamChannel<InetSocketAddress>> getServerListener(final OptionMap optionMap) {
            final String providerName = optionMap.get(RemotingOptions.AUTHENTICATION_PROVIDER);
            final ServerAuthenticationProvider authenticationProvider = connectionProviderContext.getProtocolServiceProvider(ProtocolServiceType.SERVER_AUTHENTICATION_PROVIDER, providerName == null ? "default" : providerName);
            if (authenticationProvider == null) {
                throw new IllegalArgumentException("Missing authentication provider: " + (providerName == null ? "default" : providerName));
            }
            return new ServerOpenListener(optionMap, connectionProviderContext, providerDescriptor);
        }
    }
}
