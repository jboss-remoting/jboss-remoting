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

import static java.lang.Math.min;
import static org.jboss.remoting3.remote.RemoteAuthLogger.authLog;
import static org.jboss.remoting3.remote.RemoteLogger.log;
import static org.jboss.remoting3.remote.RemoteLogger.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.Version;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.sasl.WildFlySasl;
import org.wildfly.security.sasl.util.ProtocolSaslServerFactory;
import org.wildfly.security.ssl.SSLUtils;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.SslChannel;
import org.xnio.sasl.SaslUtils;
import org.xnio.sasl.SaslWrapper;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("deprecation")
final class ServerConnectionOpenListener  implements ChannelListener<ConnectedMessageChannel> {
    private final RemoteConnection connection;
    private final ConnectionProviderContext connectionProviderContext;
    private final SaslAuthenticationFactory saslAuthenticationFactory;
    private final OptionMap optionMap;
    private final AtomicInteger retryCount = new AtomicInteger(8);
    private final String serverName;

    ServerConnectionOpenListener(final RemoteConnection connection, final ConnectionProviderContext connectionProviderContext, final SaslAuthenticationFactory saslAuthenticationFactory, final OptionMap optionMap) {
        this.connection = connection;
        this.connectionProviderContext = connectionProviderContext;
        this.saslAuthenticationFactory = saslAuthenticationFactory;
        this.optionMap = optionMap;
        if (optionMap.contains(RemotingOptions.SERVER_NAME)) {
            serverName = optionMap.get(RemotingOptions.SERVER_NAME);
        } else {
            serverName = connection.getChannel().getLocalAddress(InetSocketAddress.class).getHostName();
        }
    }



    public void handleEvent(final ConnectedMessageChannel channel) {
        final Pooled<ByteBuffer> pooled = connection.allocate();
        boolean ok = false;
        try {
            ByteBuffer sendBuffer = pooled.getResource();
            sendBuffer.put(Protocol.GREETING);
            ProtocolUtils.writeString(sendBuffer, Protocol.GRT_SERVER_NAME, serverName);
            sendBuffer.flip();
            connection.setReadListener(new Initial(), true);
            connection.send(pooled);
            ok = true;
            return;
        } catch (BufferUnderflowException | BufferOverflowException e) {
            connection.handleException(RemoteLogger.log.invalidMessage(connection));
            return;
        } finally {
            if (! ok) pooled.free();
        }
    }

    private void saslDispose(final SaslServer saslServer) {
        if (saslServer != null) {
            try {
                saslServer.dispose();
            } catch (SaslException e) {
                server.trace("Failure disposing of SaslServer", e);
            }
        }
    }

    final class Initial implements ChannelListener<ConnectedMessageChannel> {
        private boolean starttls;
        private Set<String> allowedMechanisms;
        private int version;
        private int channelsIn = 40;
        private int channelsOut = 40;
        private String remoteEndpointName;
        private int behavior = Protocol.BH_FAULTY_MSG_SIZE;

        Initial() {
            // Calculate our capabilities
            version = Protocol.VERSION;
        }

        void initialiseCapabilities() {
            final SslChannel sslChannel = connection.getSslChannel();
            final boolean channelSecure = Channels.getOption(connection.getChannel(), Options.SECURE, false);
            starttls = ! (sslChannel == null || channelSecure);
            final Set<String> foundMechanisms = new LinkedHashSet<String>();
            boolean enableExternal = false;
            try {
                // only enable EXTERNAL if there is an external auth layer
                SSLSession sslSession;
                if (sslChannel != null && (sslSession = sslChannel.getSslSession()) != null) {
                    connection.setIdentity((SecurityIdentity) sslSession.getValue(SSLUtils.SSL_SESSION_IDENTITY_KEY));
                    final Principal principal = sslSession.getPeerPrincipal();
                    // only enable EXTERNAL if there's a peer principal (else it's just ANONYMOUS)
                    if (principal != null) {
                        enableExternal = true;
                    } else {
                        server.trace("No EXTERNAL mechanism due to lack of peer principal");
                    }
                } else {
                    server.trace("No EXTERNAL mechanism due to lack of SSL");
                }
            } catch (SSLPeerUnverifiedException e) {
                server.trace("No EXTERNAL mechanism due to unverified SSL peer");
            }
            for (String mechName : saslAuthenticationFactory.getMechanismNames()) {
                if (foundMechanisms.contains(mechName)) {
                    server.tracef("Excluding repeated occurrence of mechanism %s", mechName);
                } else if (! enableExternal && mechName.equals("EXTERNAL")) {
                    server.trace("Excluding EXTERNAL due to prior config");
                } else {
                    server.tracef("Added mechanism %s", mechName);
                    foundMechanisms.add(mechName);
                }
            }
            // No need to re-order as an initial order was not passed in.
            this.allowedMechanisms = foundMechanisms;
        }



        public void handleEvent(final ConnectedMessageChannel channel) {
            final Pooled<ByteBuffer> pooledBuffer = connection.allocate();
            boolean free = true;
            try {
                final ByteBuffer receiveBuffer = pooledBuffer.getResource();
                final int res;
                try {
                    res = channel.receive(receiveBuffer);
                } catch (IOException e) {
                    connection.handleException(e);
                    return;
                }
                if (res == 0) {
                    return;
                }
                if (res == -1) {
                    log.trace("Received connection end-of-stream");
                    connection.handlePreAuthCloseRequest();
                    return;
                }
                receiveBuffer.flip();
                final byte msgType = receiveBuffer.get();
                switch (msgType) {
                    case Protocol.CONNECTION_CLOSE: {
                        server.trace("Server received connection close request");
                        connection.handlePreAuthCloseRequest();
                        return;
                    }
                    case Protocol.CONNECTION_ALIVE: {
                        server.trace("Server received connection alive");
                        connection.sendAliveResponse();
                        return;
                    }
                    case Protocol.CONNECTION_ALIVE_ACK: {
                        server.trace("Server received connection alive ack");
                        return;
                    }
                    case Protocol.CAPABILITIES: {
                        server.trace("Server received capabilities request");
                        handleClientCapabilities(receiveBuffer);
                        sendCapabilities();
                        return;
                    }
                    case Protocol.STARTTLS: {
                        server.tracef("Server received STARTTLS request");
                        final Pooled<ByteBuffer> pooled = connection.allocate();
                        boolean ok = false;
                        try {
                            ByteBuffer sendBuffer = pooled.getResource();
                            sendBuffer.put(starttls ? Protocol.STARTTLS : Protocol.NAK);
                            sendBuffer.flip();
                            connection.send(pooled);
                            ok = true;
                            if (starttls) {
                                try {
                                    connection.getSslChannel().startHandshake();
                                } catch (IOException e) {
                                    connection.handleException(e);
                                }
                            }
                            connection.setReadListener(new Initial(), true);
                            return;
                        } finally {
                            if (! ok) pooled.free();
                        }
                    }
                    case Protocol.AUTH_REQUEST: {
                        server.tracef("Server received authentication request");
                        if (retryCount.decrementAndGet() < 1) {
                            // no more tries left
                            connection.handleException(new SaslException("Too many authentication failures; connection terminated"), false);
                            return;
                        }

                        final String mechName;
                        if (version < 1) {
                            mechName = Buffers.getModifiedUtf8(receiveBuffer);
                        } else {
                            mechName = ProtocolUtils.readString(receiveBuffer);
                        }
                        final String protocol = optionMap.contains(RemotingOptions.SASL_PROTOCOL) ? optionMap.get(RemotingOptions.SASL_PROTOCOL) : RemotingOptions.DEFAULT_SASL_PROTOCOL;
                        SaslServer saslServer;
                        try {
                            saslServer = saslAuthenticationFactory.createMechanism(mechName, saslServerFactory -> new ProtocolSaslServerFactory(saslServerFactory, protocol));
                        } catch (SaslException e) {
                            server.trace("Unable to create SaslServer", e);
                            saslServer = null;
                        }
                        if (saslServer == null) {
                            rejectAuthentication(mechName);
                            return;
                        }
                        connection.getChannel().suspendReads();
                        connection.getExecutor().execute(new AuthStepRunnable(true, saslServer, pooledBuffer, remoteEndpointName, behavior, channelsIn, channelsOut));
                        free = false;
                        return;
                    }
                    default: {
                        server.unknownProtocolId(msgType);
                        connection.handleException(RemoteLogger.log.invalidMessage(connection));
                        break;
                    }
                }
            } catch (BufferUnderflowException | BufferOverflowException e) {
                connection.handleException(RemoteLogger.log.invalidMessage(connection));
                return;
            } finally {
                if (free) pooledBuffer.free();
            }
        }

        void rejectAuthentication(String mechName) {
            // reject
            authLog.rejectedInvalidMechanism(mechName);
            final Pooled<ByteBuffer> pooled = connection.allocate();
            boolean ok = false;
            try {
                final ByteBuffer sendBuffer = pooled.getResource();
                sendBuffer.put(Protocol.AUTH_REJECTED);
                sendBuffer.flip();
                connection.send(pooled);
                ok = true;
            } finally {
                if (! ok) pooled.free();
            }
        }

        void handleClientCapabilities(final ByteBuffer receiveBuffer) {
            boolean useDefaultChannels = true;
            int channelsIn = 40;
            int channelsOut = 40;
            while (receiveBuffer.hasRemaining()) {
                final byte type = receiveBuffer.get();
                final int len = receiveBuffer.get() & 0xff;
                final ByteBuffer data = Buffers.slice(receiveBuffer, len);
                switch (type) {
                    case Protocol.CAP_VERSION: {
                        final byte version = data.get();
                        server.tracef("Server received capability: version %d", Integer.valueOf(version & 0xff));
                        this.version = min(Protocol.VERSION, version & 0xff);
                        break;
                    }
                    case Protocol.CAP_ENDPOINT_NAME: {
                        remoteEndpointName = Buffers.getModifiedUtf8(data);
                        server.tracef("Server received capability: remote endpoint name \"%s\"", remoteEndpointName);
                        break;
                    }
                    case Protocol.CAP_MESSAGE_CLOSE: {
                        behavior |= Protocol.BH_MESSAGE_CLOSE;
                        // remote side must be >= 3.2.11.GA
                        // but, we'll assume it's >= 3.2.14.GA because no AS or EAP release included 3.2.8.SP1 < x < 3.2.14.GA
                        behavior &= ~Protocol.BH_FAULTY_MSG_SIZE;
                        server.tracef("Server received capability: message close protocol supported");
                        break;
                    }
                    case Protocol.CAP_VERSION_STRING: {
                        // remote side must be >= 3.2.16.GA
                        behavior &= ~Protocol.BH_FAULTY_MSG_SIZE;
                        final String remoteVersionString = Buffers.getModifiedUtf8(data);
                        server.tracef("Server received capability: remote version is \"%s\"", remoteVersionString);
                        break;
                    }
                    case Protocol.CAP_CHANNELS_IN: {
                        useDefaultChannels = false;
                        // their channels in is our channels out
                        channelsOut = ProtocolUtils.readIntData(data, len);
                        server.tracef("Server received capability: remote channels in is \"%d\"", channelsOut);
                        break;
                    }
                    case Protocol.CAP_CHANNELS_OUT: {
                        useDefaultChannels = false;
                        // their channels out is our channels in
                        channelsIn = ProtocolUtils.readIntData(data, len);
                        server.tracef("Server received capability: remote channels out is \"%d\"", channelsIn);
                        break;
                    }
                    default: {
                        server.tracef("Server received unknown capability %02x", Integer.valueOf(type & 0xff));
                        // unknown, skip it for forward compatibility.
                        break;
                    }
                }
            }
            if (! useDefaultChannels) {
                this.channelsIn = channelsIn;
                this.channelsOut = channelsOut;
            }
        }


        void sendCapabilities() {
            if (allowedMechanisms == null) {
                initialiseCapabilities();
            }

            final Pooled<ByteBuffer> pooled = connection.allocate();
            boolean ok = false;
            try {
                ByteBuffer sendBuffer = pooled.getResource();
                sendBuffer.put(Protocol.CAPABILITIES);
                ProtocolUtils.writeByte(sendBuffer, Protocol.CAP_VERSION, version);
                final String localEndpointName = connectionProviderContext.getEndpoint().getName();
                if (localEndpointName != null) {
                    // don't send a name if we're anonymous
                    ProtocolUtils.writeString(sendBuffer, Protocol.CAP_ENDPOINT_NAME, localEndpointName);
                }
                if (starttls) {
                    ProtocolUtils.writeEmpty(sendBuffer, Protocol.CAP_STARTTLS);
                }
                for (String mechName : allowedMechanisms) {
                    ProtocolUtils.writeString(sendBuffer, Protocol.CAP_SASL_MECH, mechName);
                }
                ProtocolUtils.writeEmpty(sendBuffer, Protocol.CAP_MESSAGE_CLOSE);
                ProtocolUtils.writeString(sendBuffer, Protocol.CAP_VERSION_STRING, Version.getVersionString());
                ProtocolUtils.writeInt(sendBuffer, Protocol.CAP_CHANNELS_IN, optionMap.get(RemotingOptions.MAX_INBOUND_CHANNELS, RemotingOptions.DEFAULT_MAX_INBOUND_CHANNELS));
                ProtocolUtils.writeInt(sendBuffer, Protocol.CAP_CHANNELS_OUT, optionMap.get(RemotingOptions.MAX_OUTBOUND_CHANNELS, RemotingOptions.DEFAULT_MAX_OUTBOUND_CHANNELS));
                sendBuffer.flip();
                connection.send(pooled);
                ok = true;
                return;
            } finally {
                if (! ok) pooled.free();
            }
        }
    }

    final class AuthStepRunnable implements Runnable {

        private final boolean isInitial;
        private final SaslServer saslServer;
        private final Pooled<ByteBuffer> buffer;
        private final String remoteEndpointName;
        private final int behavior;
        private final int maxInboundChannels;
        private final int maxOutboundChannels;

        AuthStepRunnable(final boolean isInitial, final SaslServer saslServer, final Pooled<ByteBuffer> buffer, final String remoteEndpointName, final int behavior, final int maxInboundChannels, final int maxOutboundChannels) {
            this.isInitial = isInitial;
            this.saslServer = saslServer;
            this.buffer = buffer;
            this.remoteEndpointName = remoteEndpointName;
            this.behavior = behavior;
            this.maxInboundChannels = maxInboundChannels;
            this.maxOutboundChannels = maxOutboundChannels;
        }

        @Override
        public void run() {
            boolean ok = false;
            boolean close = false;
            try {

                final Pooled<ByteBuffer> pooled = connection.allocate();
                try {
                    final ByteBuffer sendBuffer = pooled.getResource();
                    int p = sendBuffer.position();
                    try {
                        sendBuffer.put(Protocol.AUTH_COMPLETE);
                        if (SaslUtils.evaluateResponse(saslServer, sendBuffer, buffer.getResource())) {
                            server.tracef("Server sending authentication complete");
                            connectionProviderContext.accept(connectionContext -> {
                                final Object qop = saslServer.getNegotiatedProperty(Sasl.QOP);
                                if (!isInitial && ("auth-int".equals(qop) || "auth-conf".equals(qop))) {
                                    connection.setSaslWrapper(SaslWrapper.create(saslServer));
                                }
                                final RemoteConnectionHandler connectionHandler = new RemoteConnectionHandler(
                                    connectionContext, connection, maxInboundChannels, maxOutboundChannels, remoteEndpointName, behavior);
                                connection.getRemoteConnectionProvider().addConnectionHandler(connectionHandler);
                                final SecurityIdentity identity = (SecurityIdentity) saslServer.getNegotiatedProperty(WildFlySasl.SECURITY_IDENTITY);
                                connection.setIdentity(identity == null ? saslAuthenticationFactory.getSecurityDomain().getAnonymousSecurityIdentity() : identity);
                                connection.setReadListener(new RemoteReadListener(connectionHandler, connection), false);
                                return connectionHandler;
                            });
                        } else {
                            server.tracef("Server sending authentication challenge");
                            sendBuffer.put(p, Protocol.AUTH_CHALLENGE);
                            if (isInitial) {
                                connection.setReadListener(new Authentication(saslServer, remoteEndpointName, behavior, maxInboundChannels, maxOutboundChannels), false);
                            }
                        }
                    } catch (Throwable e) {
                        server.tracef(e, "Server sending authentication rejected");
                        sendBuffer.put(p, Protocol.AUTH_REJECTED);
                        saslDispose(saslServer);
                        if (isInitial) {
                            if (retryCount.decrementAndGet() <= 0) {
                                close = true;
                            }
                        } else {
                            connection.setReadListener(new Initial(), false);
                        }
                    }
                    sendBuffer.flip();
                    connection.send(pooled, close);
                    ok = true;
                    connection.getChannel().resumeReads();
                    return;
                } finally {
                    if (!ok) {
                        pooled.free();
                    }
                }
            } finally {
                buffer.free();
            }
        }
    }

    final class Authentication implements ChannelListener<ConnectedMessageChannel> {

        private final SaslServer saslServer;
        private final String remoteEndpointName;
        private final int behavior;
        private final int maxInboundChannels;
        private final int maxOutboundChannels;

        Authentication(final SaslServer saslServer, final String remoteEndpointName, final int behavior, final int maxInboundChannels, final int maxOutboundChannels) {
            this.saslServer = saslServer;
            this.remoteEndpointName = remoteEndpointName;
            this.behavior = behavior;
            this.maxInboundChannels = maxInboundChannels;
            this.maxOutboundChannels = maxOutboundChannels;
        }

        public void handleEvent(final ConnectedMessageChannel channel) {
            final Pooled<ByteBuffer> pooledBuffer = connection.allocate();
            boolean free = true;
            try {
                final ByteBuffer buffer = pooledBuffer.getResource();
                final int res;
                try {
                    res = channel.receive(buffer);
                } catch (IOException e) {
                    connection.handleException(e);
                    saslDispose(saslServer);
                    return;
                }
                if (res == -1) {
                    log.trace("Received connection end-of-stream");
                    connection.handlePreAuthCloseRequest();
                    saslDispose(saslServer);
                    return;
                }
                if (res == 0) {
                    return;
                }
                server.tracef("Received %s", buffer);
                buffer.flip();
                final byte msgType = buffer.get();
                switch (msgType) {
                    case Protocol.CONNECTION_CLOSE: {
                        server.trace("Server received connection close request");
                        connection.handlePreAuthCloseRequest();
                        saslDispose(saslServer);
                        return;
                    }
                    case Protocol.AUTH_RESPONSE: {
                        server.tracef("Server received authentication response");
                        connection.getChannel().suspendReads();
                        connection.getExecutor().execute(new AuthStepRunnable(false, saslServer, pooledBuffer, remoteEndpointName, behavior, maxInboundChannels, maxOutboundChannels));
                        free = false;
                        return;
                    }
                    case Protocol.CAPABILITIES: {
                        server.trace("Server received capabilities request (cancelling authentication)");
                        saslDispose(saslServer);
                        final Initial initial = new Initial();
                        connection.setReadListener(initial, true);
                        initial.handleClientCapabilities(buffer);
                        initial.sendCapabilities();
                        return;
                    }
                    default: {
                        server.unknownProtocolId(msgType);
                        connection.handleException(RemoteLogger.log.invalidMessage(connection));
                        saslDispose(saslServer);
                        break;
                    }
                }
            } catch (BufferUnderflowException | BufferOverflowException e) {
                connection.handleException(RemoteLogger.log.invalidMessage(connection));
                saslDispose(saslServer);
                return;
            } finally {
                if (free) pooledBuffer.free();
            }
        }
    }
}
