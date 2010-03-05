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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jboss.marshalling.ProviderDescriptor;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.security.ServerAuthenticationProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ProtocolServiceType;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Options;
import org.jboss.xnio.Sequence;
import org.jboss.xnio.channels.ConnectedStreamChannel;
import org.jboss.xnio.channels.SslChannel;
import org.jboss.xnio.log.Logger;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslServerFactory;

final class ServerOpenListener implements ChannelListener<ConnectedStreamChannel<InetSocketAddress>> {

    private final OptionMap optionMap;
    private final ConnectionProviderContext connectionProviderContext;
    private final ProviderDescriptor providerDescriptor;
    private static final Logger log = Loggers.serverSasl;

    ServerOpenListener(final OptionMap optionMap, final ConnectionProviderContext connectionProviderContext, final ProviderDescriptor providerDescriptor) {
        this.optionMap = optionMap;
        this.connectionProviderContext = connectionProviderContext;
        this.providerDescriptor = providerDescriptor;
    }

    public void handleEvent(final ConnectedStreamChannel<InetSocketAddress> channel) {
        try {
            channel.setOption(Options.TCP_NODELAY, Boolean.TRUE);
        } catch (IOException e) {
            // ignore
        }
        final RemoteConnection connection = new RemoteConnection(connectionProviderContext.getExecutor(), channel, optionMap, providerDescriptor);

        // Get the server authentication provider
        final String authProvider = optionMap.get(RemotingOptions.AUTHENTICATION_PROVIDER);
        if (authProvider == null) {
            log.warn("No authentication provider available");
            IoUtils.safeClose(connection);
            return;
        }
        final ServerAuthenticationProvider provider = connectionProviderContext.getProtocolServiceProvider(ProtocolServiceType.SERVER_AUTHENTICATION_PROVIDER, authProvider);
        if (provider == null) {
            log.warn("No authentication provider available");
            IoUtils.safeClose(connection);
            return;
        }

        // Calculate available server mechanisms
        final Sequence<String> mechs = optionMap.get(Options.SASL_MECHANISMS);
        final Set<String> includes = mechs != null ? new HashSet<String>(mechs) : null;
        final Map<String, Object> propertyMap = SaslUtils.createPropertyMap(optionMap);
        final Enumeration<SaslServerFactory> e = Sasl.getSaslServerFactories();
        final Map<String, SaslServerFactory> saslServerFactories = new LinkedHashMap<String, SaslServerFactory>();
        if (channel instanceof SslChannel && (includes == null | includes.contains("EXTERNAL"))) {
            final SslChannel sslChannel = (SslChannel) channel;
            final SSLSession session = sslChannel.getSslSession();
            try {
                final Principal peerPrincipal = session.getPeerPrincipal();
                // automatically the best mechanism.
                saslServerFactories.put("EXTERNAL", new ExternalSaslServerFactory(peerPrincipal));
            } catch (SSLPeerUnverifiedException e1) {
                // ignore
            }
        }
        while (e.hasMoreElements()) {
            final SaslServerFactory saslServerFactory = e.nextElement();
            for (String name : saslServerFactory.getMechanismNames(propertyMap)) {
                if (includes == null || includes.contains(name)) {
                    saslServerFactories.put(name, saslServerFactory);
                }
            }
        }
        if (saslServerFactories.isEmpty()) {
            try {
                log.trace("Sending server no-mechanisms message");
                connection.sendAuthReject("No mechanisms available");
                connection.close();
                return;
            } catch (IOException e1) {
                log.trace(e1, "Failed to send server no-mechanisms message");
                IoUtils.safeClose(connection);
                return;
            }
        }

        // Send server greeting packet...
        final ByteBuffer buffer = connection.allocate();
        // length placeholder
        buffer.putInt(0);
        buffer.put(RemoteProtocol.GREETING);
        // version ID
        GreetingUtils.writeByte(buffer, RemoteProtocol.GREETING_VERSION, RemoteProtocol.VERSION);
        // marshaller versions
        final int[] versions = providerDescriptor.getSupportedVersions();
        for (int version : versions) {
            GreetingUtils.writeInt(buffer, RemoteProtocol.GREETING_MARSHALLER_VERSION, version);
        }
        // SASL server mechs
        for (String name : saslServerFactories.keySet()) {
            GreetingUtils.writeString(buffer, RemoteProtocol.GREETING_SASL_MECH, name);
            log.trace("Offering SASL mechanism %s", name);
        }
        GreetingUtils.writeString(buffer, RemoteProtocol.GREETING_ENDPOINT_NAME, connectionProviderContext.getEndpoint().getName());
        // that's it!
        buffer.flip();
        buffer.putInt(0, buffer.remaining() - 4);
        channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel<InetSocketAddress>>() {
            public void handleEvent(final ConnectedStreamChannel<InetSocketAddress> channel) {
                for (;;) {
                    while (buffer.hasRemaining()) {
                        final int res;
                        try {
                            res = channel.write(buffer);
                        } catch (IOException e1) {
                            log.trace(e1, "Failed to send server greeting message");
                            IoUtils.safeClose(connection);
                            connection.free(buffer);
                            return;
                        }
                        if (res == 0) {
                            channel.resumeWrites();
                            return;
                        }
                    }
                    connection.free(buffer);
                    try {
                        while (! channel.flush());
                    } catch (IOException e) {
                        log.trace(e, "Failed to flush server greeting message");
                        IoUtils.safeClose(connection);
                        return;
                    }
                    log.trace("Server sent greeting message");
                    channel.resumeReads();
                    return;
                }
            }
        });
        connection.setMessageHandler(new ServerGreetingHandler(connection, connectionProviderContext, saslServerFactories, provider, propertyMap));
        // and send the greeting
        channel.resumeWrites();
    }
}
