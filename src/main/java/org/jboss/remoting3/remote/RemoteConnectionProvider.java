/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.security.ServerAuthenticationProvider;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.ChannelThreadPool;
import org.xnio.ConnectionChannelThread;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.ReadChannelThread;
import org.xnio.Result;
import org.xnio.Sequence;
import org.xnio.WriteChannelThread;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.FramedMessageChannel;
import org.xnio.sasl.SaslUtils;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslServerFactory;

import static org.jboss.remoting3.remote.RemoteLogger.log;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteConnectionProvider implements ConnectionProvider {

    private final ProviderInterface providerInterface = new ProviderInterface();
    private final Xnio xnio;
    private final ChannelThreadPool<ReadChannelThread> readThreadPool;
    private final ChannelThreadPool<WriteChannelThread> writeThreadPool;
    private final ChannelThreadPool<ConnectionChannelThread> connectThreadPool;
    private final ConnectionProviderContext connectionProviderContext;
    private final Pool<ByteBuffer> bufferPool;

    RemoteConnectionProvider(final Xnio xnio, final Pool<ByteBuffer> bufferPool, final ChannelThreadPool<ReadChannelThread> readThreadPool, final ChannelThreadPool<WriteChannelThread> writeThreadPool, final ChannelThreadPool<ConnectionChannelThread> connectThreadPool, final ConnectionProviderContext connectionProviderContext) {
        this.xnio = xnio;
        this.readThreadPool = readThreadPool;
        this.writeThreadPool = writeThreadPool;
        this.bufferPool = bufferPool;
        this.connectThreadPool = connectThreadPool;
        this.connectionProviderContext = connectionProviderContext;
    }

    public Cancellable connect(final URI uri, final OptionMap connectOptions, final Result<ConnectionHandlerFactory> result, final CallbackHandler callbackHandler) throws IllegalArgumentException {
        boolean secure = connectOptions.get(Options.SECURE, false);
        final InetSocketAddress destination;
        try {
            destination = new InetSocketAddress(InetAddress.getByName(uri.getHost()), uri.getPort());
        } catch (UnknownHostException e) {
            result.setException(e);
            return IoUtils.nullCancellable();
        }
        ChannelListener<ConnectedStreamChannel> openListener = new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                try {
                    channel.setOption(Options.TCP_NODELAY, Boolean.TRUE);
                } catch (IOException e) {
                    // ignore
                }
                final FramedMessageChannel messageChannel = new FramedMessageChannel(channel, bufferPool.allocate(), bufferPool.allocate());
                final RemoteConnection remoteConnection = new RemoteConnection(bufferPool, messageChannel, connectOptions, connectionProviderContext.getExecutor());
                messageChannel.getWriteSetter().set(remoteConnection.getWriteListener());
                final ClientConnectionOpenListener openListener = new ClientConnectionOpenListener(remoteConnection, callbackHandler, AccessController.getContext());
                openListener.handleEvent(messageChannel);
            }
        };
        if (secure) {
            try {
                return xnio.connectSsl(destination, connectThreadPool.getThread(), readThreadPool.getThread(), writeThreadPool.getThread(), openListener, connectOptions);
            } catch (NoSuchProviderException e) {
                result.setException(new IOException(e));
                return IoUtils.nullCancellable();
            } catch (NoSuchAlgorithmException e) {
                result.setException(new IOException(e));
                return IoUtils.nullCancellable();
            }
        } else {
            return xnio.connectStream(destination, connectThreadPool.getThread(), readThreadPool.getThread(), writeThreadPool.getThread(), openListener, null, connectOptions);
        }
    }

    public Object getProviderInterface() {
        return providerInterface;
    }

    private final class ProviderInterface implements NetworkServerProvider {

        public ChannelListener<AcceptingChannel<ConnectedStreamChannel>> getServerListener(final OptionMap optionMap, final ServerAuthenticationProvider authenticationProvider) {
            return new AcceptListener(optionMap, authenticationProvider);
        }
    }

    private final class AcceptListener implements ChannelListener<AcceptingChannel<ConnectedStreamChannel>> {

        private final OptionMap serverOptionMap;
        private final ServerAuthenticationProvider serverAuthenticationProvider;

        AcceptListener(final OptionMap serverOptionMap, final ServerAuthenticationProvider serverAuthenticationProvider) {
            this.serverOptionMap = serverOptionMap;
            this.serverAuthenticationProvider = serverAuthenticationProvider;
        }

        public void handleEvent(final AcceptingChannel<ConnectedStreamChannel> channel) {
            ConnectedStreamChannel accepted = null;
            try {
                accepted = channel.accept(readThreadPool.getThread(), writeThreadPool.getThread());
                if (accepted == null) {
                    return;
                }
            } catch (IOException e) {
                log.failedToAccept(e);
            }

            final FramedMessageChannel messageChannel = new FramedMessageChannel(accepted, bufferPool.allocate(), bufferPool.allocate());
            RemoteConnection connection = new RemoteConnection(bufferPool, messageChannel, serverOptionMap, connectionProviderContext.getExecutor());
            messageChannel.getWriteSetter().set(connection.getWriteListener());
            final Map<String, SaslServerFactory> allowedMechanisms;
            boolean ok = false;
            final Map<String, ?> propertyMap;
            final Pooled<ByteBuffer> pooled = bufferPool.allocate();
            try {
                ByteBuffer buffer = pooled.getResource();
                buffer.put(Protocol.GREETING);
                ProtocolUtils.writeByte(buffer, Protocol.GREETING_VERSION, 0);
                propertyMap = SaslUtils.createPropertyMap(serverOptionMap);
                final Sequence<String> saslMechs = serverOptionMap.get(Options.SASL_MECHANISMS);
                final Set<String> restrictions = saslMechs == null ? null : new HashSet<String>(saslMechs);
                final Enumeration<SaslServerFactory> factories = Sasl.getSaslServerFactories();
                allowedMechanisms = new LinkedHashMap<String, SaslServerFactory>();
                try {
                    if (channel instanceof ConnectedSslStreamChannel) {
                        ConnectedSslStreamChannel sslStreamChannel = (ConnectedSslStreamChannel) channel;
                        allowedMechanisms.put("EXTERNAL", new ExternalSaslServerFactory(sslStreamChannel.getSslSession().getPeerPrincipal()));
                    }
                } catch (IOException e) {
                    // ignore
                }
                while (factories.hasMoreElements()) {
                    SaslServerFactory factory = factories.nextElement();
                    for (String mechName : factory.getMechanismNames(propertyMap)) {
                        if ((restrictions == null || restrictions.contains(mechName)) && ! allowedMechanisms.containsKey(mechName)) {
                            allowedMechanisms.put(mechName, factory);
                        }
                    }
                }
                if (saslMechs != null) for (String name : saslMechs) {
                    ProtocolUtils.writeString(buffer, Protocol.GREETING_SASL_MECH, name);
                }
                ProtocolUtils.writeString(buffer, Protocol.GREETING_ENDPOINT_NAME, connectionProviderContext.getEndpoint().getName());
                ProtocolUtils.writeShort(buffer, Protocol.GREETING_CHANNEL_LIMIT, serverOptionMap.get(RemotingOptions.MAX_INBOUND_CHANNELS, Protocol.DEFAULT_CHANNEL_COUNT));
                buffer.flip();
                connection.send(pooled);
                ok = true;
            } finally {
                if (! ok) {
                    pooled.free();
                }
            }

            messageChannel.getReadSetter().set(new ServerConnectionGreetingListener(connection, allowedMechanisms, serverAuthenticationProvider, serverOptionMap, connectionProviderContext, propertyMap));
            messageChannel.resumeReads();
        }
    }
}
