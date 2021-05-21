/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3.remote;

import static java.lang.Math.min;
import static org.jboss.remoting3._private.Messages.log;
import static org.jboss.remoting3._private.Messages.server;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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
import org.wildfly.security.auth.principal.AnonymousPrincipal;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.sasl.WildFlySasl;
import org.wildfly.security.sasl.util.PropertiesSaslServerFactory;
import org.wildfly.security.sasl.util.ProtocolSaslServerFactory;
import org.wildfly.security.sasl.util.SSLSaslServerFactory;
import org.wildfly.security.sasl.util.ServerNameSaslServerFactory;
import org.wildfly.security.sasl.util.SocketAddressCallbackSaslServerFactory;
import org.wildfly.security.ssl.SSLUtils;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pooled;
import org.xnio.Property;
import org.xnio.Sequence;
import org.xnio.channels.Channels;
import org.xnio.channels.SslChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.sasl.SaslUtils;
import org.xnio.sasl.SaslWrapper;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("deprecation")
final class ServerConnectionOpenListener  implements ChannelListener<ConduitStreamSourceChannel> {
    private final RemoteConnection connection;
    private final ConnectionProviderContext connectionProviderContext;
    private final SaslAuthenticationFactory saslAuthenticationFactory;
    private final OptionMap optionMap;
    private final AtomicInteger retryCount = new AtomicInteger();
    private final String serverName;

    ServerConnectionOpenListener(final RemoteConnection connection, final ConnectionProviderContext connectionProviderContext, final SaslAuthenticationFactory saslAuthenticationFactory, final OptionMap optionMap) {
        this.connection = connection;
        this.connectionProviderContext = connectionProviderContext;
        this.saslAuthenticationFactory = saslAuthenticationFactory;
        this.optionMap = optionMap;
        if (optionMap.contains(RemotingOptions.SERVER_NAME)) {
            serverName = optionMap.get(RemotingOptions.SERVER_NAME);
        } else {
            serverName = InetUtils.determineServerName(connection.getLocalAddress().getHostName());
        }
    }



    public void handleEvent(final ConduitStreamSourceChannel channel) {
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
            connection.handleException(log.invalidMessage(connection));
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

    private void resumeReads() {
        connection.getMessageReader().getSourceChannel().getIoThread().execute(new Runnable() {
            @Override
            public void run() {
                connection.getMessageReader().resumeReads();
            }
        });
    }

    private void suspendReads() {
        connection.getMessageReader().getSourceChannel().getIoThread().execute(new Runnable() {
            @Override
            public void run() {
                connection.getMessageReader().suspendReads();
            }
        });
    }

    final class Initial implements ChannelListener<ConduitStreamSourceChannel> {
        private boolean starttls;
        private Set<String> allowedMechanisms;
        private int version;
        private int maxInboundChannels = optionMap.get(RemotingOptions.MAX_INBOUND_CHANNELS, RemotingOptions.DEFAULT_MAX_INBOUND_CHANNELS);
        private int maxOutboundChannels = optionMap.get(RemotingOptions.MAX_OUTBOUND_CHANNELS, RemotingOptions.DEFAULT_MAX_OUTBOUND_CHANNELS);
        private String remoteEndpointName;
        private int behavior = Protocol.BH_FAULTY_MSG_SIZE;
        private boolean authCap;

        Initial() {
            // Calculate our capabilities
            version = Protocol.VERSION;
        }

        void initialiseCapabilities() {
            final SslChannel sslChannel = connection.getSslChannel();
            final boolean channelSecure = sslChannel != null && Channels.getOption(sslChannel, Options.SECURE, false);
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
            int cnt = 0;
            for (String mechName : saslAuthenticationFactory.getMechanismNames()) {
                if (foundMechanisms.contains(mechName)) {
                    server.tracef("Excluding repeated occurrence of mechanism %s", mechName);
                } else if (! enableExternal && mechName.equals("EXTERNAL")) {
                    server.trace("Excluding EXTERNAL due to prior config");
                } else {
                    server.tracef("Added mechanism %s", mechName);
                    foundMechanisms.add(mechName);
                    cnt ++;
                }
            }
            retryCount.set(cnt);
            // No need to re-order as an initial order was not passed in.
            this.allowedMechanisms = foundMechanisms;
        }



        public void handleEvent(final ConduitStreamSourceChannel channel) {
            final Pooled<ByteBuffer> message;
            try {
                message = connection.getMessageReader().getMessage();
            } catch (IOException e) {
                connection.handleException(e);
                return;
            }
            if (message == MessageReader.EOF_MARKER) {
                log.trace("Received connection end-of-stream");
                connection.handlePreAuthCloseRequest();
                return;
            }
            if (message == null) {
                return;
            }
            boolean free = true;
            try {
                final ByteBuffer receiveBuffer = message.getResource();
                server.tracef("Received %s", receiveBuffer);
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
                            connection.setReadListener(new Initial(), true);
                            if (starttls) {
                                connection.send(RemoteConnection.STARTTLS_SENTINEL);
                            }
                            return;
                        } finally {
                            if (! ok) pooled.free();
                        }
                    }
                    case Protocol.AUTH_REQUEST: {
                        server.tracef("Server received authentication request");
                        if (retryCount.getAndDecrement() <= 0) {
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
                        final String protocol = optionMap.get(RemotingOptions.SASL_PROTOCOL, RemotingOptions.DEFAULT_SASL_PROTOCOL);
                        final Map<String, String> saslProperties = getSaslProperties(optionMap);
                        final SslChannel sslChannel = connection.getSslChannel();
                        final SSLSession sslSession = sslChannel == null ? null : sslChannel.getSslSession();

                        SaslServer saslServer;
                        try {
                            saslServer = saslAuthenticationFactory.createMechanism(mechName, saslServerFactory -> {
                                saslServerFactory = new SocketAddressCallbackSaslServerFactory(saslServerFactory, connection.getLocalAddress(), connection.getPeerAddress());
                                saslServerFactory = sslSession != null ? new SSLSaslServerFactory(saslServerFactory, () -> sslSession) : saslServerFactory;
                                saslServerFactory = new ServerNameSaslServerFactory(saslServerFactory, serverName);
                                saslServerFactory = new ProtocolSaslServerFactory(saslServerFactory, protocol);
                                saslServerFactory = saslProperties != null ? new PropertiesSaslServerFactory(saslServerFactory, saslProperties) : saslServerFactory;
                                return saslServerFactory;
                            });
                        } catch (Throwable e) {
                            server.trace("Unable to create SaslServer", e);
                            saslServer = null;
                        }
                        if (saslServer == null) {
                            rejectAuthentication(mechName);
                            return;
                        }
                        suspendReads();
                        connection.getExecutor().execute(new AuthStepRunnable(true, saslServer, message, remoteEndpointName, behavior, maxInboundChannels, maxOutboundChannels, authCap, null));
                        free = false;
                        return;
                    }
                    default: {
                        server.unknownProtocolId(msgType);
                        connection.handleException(log.invalidMessage(connection));
                        break;
                    }
                }
            } catch (BufferUnderflowException | BufferOverflowException e) {
                connection.handleException(log.invalidMessage(connection));
                return;
            } finally {
                if (free) message.free();
            }
        }

        private Map<String, String> getSaslProperties(final OptionMap optionMap) {
            Map<String, String> saslProperties = null;
            final Sequence<Property> value = optionMap.get(Options.SASL_PROPERTIES);
            if (value != null) {
                saslProperties = new HashMap<>(value.size());
                for (Property property : value) {
                    saslProperties.put(property.getKey(), (String) property.getValue());
                }
            }
            return saslProperties;
        }

        void rejectAuthentication(String mechName) {
            // reject
            log.rejectedInvalidMechanism(mechName);
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
            boolean authCap = false;
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
                        // their channels in is our channels out
                        final int channelsOut = ProtocolUtils.readIntData(data, len);
                        maxOutboundChannels = Math.min(maxOutboundChannels, channelsOut);
                        server.tracef("Server received capability: remote channels in is \"%d\"; resulting max outbound channels value is \"%d\"",
                                channelsOut, maxOutboundChannels);
                        break;
                    }
                    case Protocol.CAP_CHANNELS_OUT: {
                        // their channels out is our channels in
                        final int channelsIn = ProtocolUtils.readIntData(data, len);
                        maxInboundChannels = Math.min(maxInboundChannels, channelsIn);
                        server.tracef("Server received capability: remote channels out is \"%d\"; resulting max inbound channels value is \"%d\"",
                                channelsIn, maxInboundChannels);
                        break;
                    }
                    case Protocol.CAP_AUTHENTICATION: {
                        authCap = true;
                        server.trace("Server received capability: authentication service");
                        break;
                    }
                    default: {
                        server.tracef("Server received unknown capability %02x", Integer.valueOf(type & 0xff));
                        // unknown, skip it for forward compatibility.
                        break;
                    }
                }
            }
            this.authCap = authCap;
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
                ProtocolUtils.writeEmpty(sendBuffer, Protocol.CAP_AUTHENTICATION);
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
        private final boolean authCap;
        private final Set<String> offeredMechanisms;

        AuthStepRunnable(final boolean isInitial, final SaslServer saslServer, final Pooled<ByteBuffer> buffer, final String remoteEndpointName, final int behavior, final int maxInboundChannels, final int maxOutboundChannels, final boolean authCap, final Set<String> offeredMechanisms) {
            this.isInitial = isInitial;
            this.saslServer = saslServer;
            this.buffer = buffer;
            this.remoteEndpointName = remoteEndpointName;
            this.behavior = behavior;
            this.maxInboundChannels = maxInboundChannels;
            this.maxOutboundChannels = maxOutboundChannels;
            this.authCap = authCap;
            this.offeredMechanisms = offeredMechanisms;
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
                                final String peerName = connection.getPeerAddress().getHostName();
                                final RemoteConnectionHandler connectionHandler = new RemoteConnectionHandler(
                                    connectionContext, connection, maxInboundChannels, maxOutboundChannels, AnonymousPrincipal.getInstance(), remoteEndpointName, behavior, authCap, offeredMechanisms, peerName, serverName);
                                connection.getRemoteConnectionProvider().addConnectionHandler(connectionHandler);
                                final SecurityIdentity identity = (SecurityIdentity) saslServer.getNegotiatedProperty(WildFlySasl.SECURITY_IDENTITY);
                                connection.setIdentity(identity == null ? saslAuthenticationFactory.getSecurityDomain().getAnonymousSecurityIdentity() : identity);
                                connection.setReadListener(new RemoteReadListener(connectionHandler, connection), false);
                                return connectionHandler;
                            }, saslAuthenticationFactory);
                        } else {
                            server.tracef("Server sending authentication challenge");
                            sendBuffer.put(p, Protocol.AUTH_CHALLENGE);
                            if (isInitial) {
                                connection.setReadListener(new Authentication(saslServer, remoteEndpointName, behavior, maxInboundChannels, maxOutboundChannels, authCap, offeredMechanisms), false);
                            }
                        }
                    } catch (Throwable e) {
                        server.tracef(e, "Server sending authentication rejected");
                        sendBuffer.put(p, Protocol.AUTH_REJECTED);
                        saslDispose(saslServer);
                        if (retryCount.get() <= 0) {
                            server.tracef("No more authentication attempts allowed, closing the connection");
                            close = true;
                        } else if (!isInitial) {
                            connection.setReadListener(new Initial(), false);
                        }
                    }
                    sendBuffer.flip();
                    connection.send(pooled, close);
                    ok = true;
                    resumeReads();
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

    final class Authentication implements ChannelListener<ConduitStreamSourceChannel> {

        private final SaslServer saslServer;
        private final String remoteEndpointName;
        private final int behavior;
        private final int maxInboundChannels;
        private final int maxOutboundChannels;
        private final boolean authCap;
        private final Set<String> offeredMechanisms;

        Authentication(final SaslServer saslServer, final String remoteEndpointName, final int behavior, final int maxInboundChannels, final int maxOutboundChannels, final boolean authCap, final Set<String> offeredMechanisms) {
            this.saslServer = saslServer;
            this.remoteEndpointName = remoteEndpointName;
            this.behavior = behavior;
            this.maxInboundChannels = maxInboundChannels;
            this.maxOutboundChannels = maxOutboundChannels;
            this.authCap = authCap;
            this.offeredMechanisms = offeredMechanisms;
        }

        public void handleEvent(final ConduitStreamSourceChannel channel) {
            final Pooled<ByteBuffer> message;
            try {
                message = connection.getMessageReader().getMessage();
            } catch (IOException e) {
                connection.handleException(e);
                saslDispose(saslServer);
                return;
            }
            if (message == MessageReader.EOF_MARKER) {
                log.trace("Received connection end-of-stream");
                connection.handlePreAuthCloseRequest();
                saslDispose(saslServer);
                return;
            }
            if (message == null) {
                return;
            }
            boolean free = true;
            try {
                final ByteBuffer buffer = message.getResource();
                server.tracef("Received %s", buffer);
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
                        suspendReads();
                        connection.getExecutor().execute(new AuthStepRunnable(false, saslServer, message, remoteEndpointName, behavior, maxInboundChannels, maxOutboundChannels, authCap, offeredMechanisms));
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
                        connection.handleException(log.invalidMessage(connection));
                        saslDispose(saslServer);
                        break;
                    }
                }
            } catch (BufferUnderflowException | BufferOverflowException e) {
                connection.handleException(log.invalidMessage(connection));
                saslDispose(saslServer);
                return;
            } finally {
                if (free) message.free();
            }
        }
    }
}
