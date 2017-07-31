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

import java.io.IOException;
import java.net.URI;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.Version;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.principal.AnonymousPrincipal;
import org.wildfly.security.sasl.WildFlySasl;
import org.wildfly.security.sasl.util.ServerNameSaslClientFactory;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pooled;
import org.xnio.Sequence;
import org.xnio.channels.SslChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.sasl.SaslWrapper;
import org.xnio.ssl.SslConnection;

import javax.net.ssl.SSLSession;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslException;

import static java.security.AccessController.doPrivileged;
import static org.jboss.remoting3._private.Messages.client;
import static org.xnio.sasl.SaslUtils.EMPTY_BYTES;

@SuppressWarnings("deprecation")
final class ClientConnectionOpenListener implements ChannelListener<ConduitStreamSourceChannel> {

    private final URI uri;
    private final RemoteConnection connection;
    private final ConnectionProviderContext connectionProviderContext;
    private final AuthenticationConfiguration configuration;
    private final UnaryOperator<SaslClientFactory> saslClientFactoryOperator;
    private final Collection<String> serverMechs;
    private final OptionMap optionMap;
    private final Map<String, Throwable> failedMechs = new LinkedHashMap<>();
    private final Set<String> allowedMechs;
    private final Set<String> disallowedMechs;
    static final AuthenticationContextConfigurationClient AUTH_CONFIGURATION_CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);

    ClientConnectionOpenListener(final URI uri, final RemoteConnection connection, final ConnectionProviderContext connectionProviderContext, final AuthenticationConfiguration configuration, final UnaryOperator<SaslClientFactory> saslClientFactoryOperator, final Collection<String> serverMechs, final OptionMap optionMap) {
        this.uri = uri;
        this.connection = connection;
        this.connectionProviderContext = connectionProviderContext;
        this.configuration = configuration;
        this.saslClientFactoryOperator = saslClientFactoryOperator;
        this.serverMechs = serverMechs;
        this.optionMap = optionMap;
        final Sequence<String> allowedMechs = optionMap.get(Options.SASL_MECHANISMS);
        final Sequence<String> disallowedMechs = optionMap.get(Options.SASL_DISALLOWED_MECHANISMS);
        this.allowedMechs = allowedMechs == null ? null : new HashSet<String>(allowedMechs);
        this.disallowedMechs = disallowedMechs == null ? Collections.emptySet() : new HashSet<String>(disallowedMechs);
    }

    public void handleEvent(final ConduitStreamSourceChannel channel) {
        connection.setReadListener(new Greeting(), true);
    }

    SaslException allMechanismsFailed() {
        final StringBuilder b = new StringBuilder();
        b.append("Authentication failed: all available authentication mechanisms failed:");
        for (Map.Entry<String, Throwable> entry : failedMechs.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue().toString();
            b.append("\n   ").append(key).append(": ").append(value);
        }
        final SaslException saslException = new SaslException(b.toString());
        for (Throwable cause : failedMechs.values()) {
            saslException.addSuppressed(cause);
        }
        return saslException;
    }

    void sendCapRequest(final String remoteServerName) {
        client.trace("Client sending capabilities request");
        // Prepare the request message body
        final Pooled<ByteBuffer> pooledSendBuffer = connection.allocate();
        boolean ok = false;
        try {
            final ByteBuffer sendBuffer = pooledSendBuffer.getResource();
            sendBuffer.put(Protocol.CAPABILITIES);
            ProtocolUtils.writeByte(sendBuffer, Protocol.CAP_VERSION, Protocol.VERSION);
            final String localEndpointName = connectionProviderContext.getEndpoint().getName();
            if (localEndpointName != null) {
                ProtocolUtils.writeString(sendBuffer, Protocol.CAP_ENDPOINT_NAME, localEndpointName);
            }
            ProtocolUtils.writeEmpty(sendBuffer, Protocol.CAP_MESSAGE_CLOSE);
            ProtocolUtils.writeString(sendBuffer, Protocol.CAP_VERSION_STRING, Version.getVersionString());
            ProtocolUtils.writeInt(sendBuffer, Protocol.CAP_CHANNELS_IN, optionMap.get(RemotingOptions.MAX_INBOUND_CHANNELS, RemotingOptions.DEFAULT_MAX_INBOUND_CHANNELS));
            ProtocolUtils.writeInt(sendBuffer, Protocol.CAP_CHANNELS_OUT, optionMap.get(RemotingOptions.MAX_OUTBOUND_CHANNELS, RemotingOptions.DEFAULT_MAX_OUTBOUND_CHANNELS));
            ProtocolUtils.writeEmpty(sendBuffer, Protocol.CAP_AUTHENTICATION);
            final Collection<String> serverMechs = this.serverMechs;
            if (serverMechs != null) {
                for (String name : serverMechs) {
                    ProtocolUtils.writeString(sendBuffer, Protocol.CAP_SASL_MECH, name);
                }
            }
            sendBuffer.flip();
            connection.setReadListener(new Capabilities(remoteServerName, uri), true);
            connection.send(pooledSendBuffer);
            ok = true;
            // all set
            return;
        } finally {
            if (! ok) {
                pooledSendBuffer.free();
            }
        }
    }

    private void saslDispose(final SaslClient saslClient) {
        if (saslClient != null) {
            try {
                saslClient.dispose();
            } catch (SaslException e) {
                client.trace("Failure disposing of SaslClient", e);
            }
        }
    }

    final class Greeting implements ChannelListener<ConduitStreamSourceChannel> {

        public void handleEvent(final ConduitStreamSourceChannel channel) {
            final Pooled<ByteBuffer> message;
            try {
                message = connection.getMessageReader().getMessage();
            } catch (IOException e) {
                connection.handleException(e);
                return;
            }
            if (message == MessageReader.EOF_MARKER) {
                connection.handleException(client.abruptClose(connection));
                return;
            }
            if (message == null) {
                return;
            }
            try {
                final ByteBuffer receiveBuffer = message.getResource();
                client.tracef("Received %s", receiveBuffer);
                String remoteServerName = null;
                final byte msgType = receiveBuffer.get();
                switch (msgType) {
                    case Protocol.CONNECTION_ALIVE: {
                        client.trace("Client received connection alive");
                        connection.sendAliveResponse();
                        return;
                    }
                    case Protocol.CONNECTION_ALIVE_ACK: {
                        client.trace("Client received connection alive ack");
                        return;
                    }
                    case Protocol.CONNECTION_CLOSE: {
                        client.trace("Client received connection close request");
                        connection.handlePreAuthCloseRequest();
                        return;
                    }
                    case Protocol.GREETING: {
                        client.trace("Client received greeting");
                        while (receiveBuffer.hasRemaining()) {
                            final byte type = receiveBuffer.get();
                            final int len = receiveBuffer.get() & 0xff;
                            final ByteBuffer data = Buffers.slice(receiveBuffer, len);
                            switch (type) {
                                case Protocol.GRT_SERVER_NAME: {
                                    remoteServerName = Buffers.getModifiedUtf8(data);
                                    client.tracef("Client received server name: %s", remoteServerName);
                                    break;
                                }
                                default: {
                                    client.tracef("Client received unknown greeting message %02x", Integer.valueOf(type & 0xff));
                                    // unknown, skip it for forward compatibility.
                                    break;
                                }
                            }
                        }
                        if (remoteServerName == null) {
                            // they didn't give their name; guess it from the IP
                            remoteServerName = connection.getPeerAddress().getHostName();
                        }
                        sendCapRequest(remoteServerName);
                        return;
                    }
                    default: {
                        client.unknownProtocolId(msgType);
                        connection.handleException(client.invalidMessage(connection));
                        return;
                    }
                }
            } catch (BufferUnderflowException | BufferOverflowException e) {
                connection.handleException(client.invalidMessage(connection));
                return;
            } finally {
                message.free();
            }
        }

    }

    final class Capabilities implements ChannelListener<ConduitStreamSourceChannel> {

        private final String remoteServerName;
        private final URI uri;

        Capabilities(final String remoteServerName, final URI uri) {
            this.remoteServerName = remoteServerName;
            this.uri = uri;
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
                if(!failedMechs.isEmpty()){
                    connection.handleException(allMechanismsFailed());
                    return;
                }
                connection.handleException(client.abruptClose(connection));
                return;
            }
            if (message == null) {
                return;
            }
            try {
                final ByteBuffer receiveBuffer = message.getResource();
                boolean starttls = false;
                final Set<String> serverSaslMechs = new LinkedHashSet<String>();
                final Set<String> offeredMechs = new LinkedHashSet<>();
                final byte msgType = receiveBuffer.get();
                switch (msgType) {
                    case Protocol.CONNECTION_ALIVE: {
                        client.trace("Client received connection alive");
                        connection.sendAliveResponse();
                        return;
                    }
                    case Protocol.CONNECTION_ALIVE_ACK: {
                        client.trace("Client received connection alive ack");
                        return;
                    }
                    case Protocol.CONNECTION_CLOSE: {
                        client.trace("Client received connection close request");
                        connection.handlePreAuthCloseRequest();
                        return;
                    }
                    case Protocol.CAPABILITIES: {
                        client.trace("Client received capabilities response");
                        String remoteEndpointName = null;
                        int version = Protocol.VERSION;
                        int behavior = Protocol.BH_FAULTY_MSG_SIZE;
                        boolean useDefaultChannels = true;
                        int channelsIn = 40;
                        int channelsOut = 40;
                        boolean authCap = false;
                        while (receiveBuffer.hasRemaining()) {
                            final byte type = receiveBuffer.get();
                            final int len = receiveBuffer.get() & 0xff;
                            final ByteBuffer data = Buffers.slice(receiveBuffer, len);
                            switch (type) {
                                case Protocol.CAP_VERSION: {
                                    version = data.get() & 0xff;
                                    client.tracef("Client received capability: version %d", Integer.valueOf(version & 0xff));
                                    break;
                                }
                                case Protocol.CAP_SASL_MECH: {
                                    final String mechName = Buffers.getModifiedUtf8(data);
                                    client.tracef("Client received capability: SASL mechanism %s", mechName);
                                    offeredMechs.add(mechName);
                                    if (! failedMechs.containsKey(mechName) && ! disallowedMechs.contains(mechName) && (allowedMechs == null || allowedMechs.contains(mechName))) {
                                        client.tracef("SASL mechanism %s added to allowed set", mechName);
                                        serverSaslMechs.add(mechName);
                                    }
                                    break;
                                }
                                case Protocol.CAP_STARTTLS: {
                                    client.trace("Client received capability: STARTTLS");
                                    starttls = true;
                                    break;
                                }
                                case Protocol.CAP_ENDPOINT_NAME: {
                                    remoteEndpointName = Buffers.getModifiedUtf8(data);
                                    client.tracef("Client received capability: remote endpoint name \"%s\"", remoteEndpointName);
                                    break;
                                }
                                case Protocol.CAP_MESSAGE_CLOSE: {
                                    behavior |= Protocol.BH_MESSAGE_CLOSE;
                                    // remote side must be >= 3.2.11.GA
                                    // but, we'll assume it's >= 3.2.14.GA because no AS or EAP release included 3.2.8.SP1 < x < 3.2.14.GA
                                    behavior &= ~Protocol.BH_FAULTY_MSG_SIZE;
                                    client.tracef("Client received capability: message close protocol supported");
                                    break;
                                }
                                case Protocol.CAP_VERSION_STRING: {
                                    // remote side must be >= 3.2.16.GA
                                    behavior &= ~Protocol.BH_FAULTY_MSG_SIZE;
                                    final String remoteVersionString = Buffers.getModifiedUtf8(data);
                                    client.tracef("Client received capability: remote version is \"%s\"", remoteVersionString);
                                    break;
                                }
                                case Protocol.CAP_CHANNELS_IN: {
                                    useDefaultChannels = false;
                                    // their channels in is our channels out
                                    channelsOut = ProtocolUtils.readIntData(data, len);
                                    client.tracef("Client received capability: remote channels in is \"%d\"", channelsOut);
                                    break;
                                }
                                case Protocol.CAP_CHANNELS_OUT: {
                                    useDefaultChannels = false;
                                    // their channels out is our channels in
                                    channelsIn = ProtocolUtils.readIntData(data, len);
                                    client.tracef("Client received capability: remote channels out is \"%d\"", channelsIn);
                                    break;
                                }
                                case Protocol.CAP_AUTHENTICATION: {
                                    authCap = true;
                                    client.trace("Client received capability: authentication service");
                                    break;
                                }
                                default: {
                                    client.tracef("Client received unknown capability %02x", Integer.valueOf(type & 0xff));
                                    // unknown, skip it for forward compatibility.
                                    break;
                                }
                            }
                        }
                        if (useDefaultChannels) {
                            channelsIn = 40;
                            channelsOut = 40;
                        }
                        if (starttls) {
                            // only initiate starttls if not forbidden by config and possible on the connection
                            if (optionMap.get(Options.SSL_STARTTLS, true) && connection.getConnection() instanceof SslConnection) {
                                // Prepare the request message body
                                final Pooled<ByteBuffer> pooledSendBuffer = connection.allocate();
                                boolean ok = false;
                                try {
                                    final ByteBuffer sendBuffer = pooledSendBuffer.getResource();
                                    sendBuffer.put(Protocol.STARTTLS);
                                    sendBuffer.flip();
                                    connection.setReadListener(new StartTls(remoteServerName), true);
                                    connection.send(pooledSendBuffer);
                                    ok = true;
                                    // all set
                                    return;
                                } finally {
                                    if (! ok) pooledSendBuffer.free();
                                }
                            }
                        }

                        if (serverSaslMechs.isEmpty()) {
                            if (failedMechs.isEmpty()) {
                                connection.handleException(new SaslException("Authentication failed: the server presented no authentication mechanisms"));
                            } else {
                                // At this point we have been attempting to use mechanisms as they have been presented to us but we have now exhausted the list.
                                connection.handleException(allMechanismsFailed());
                            }
                            return;
                        }

                        // OK now send our authentication request
                        final AuthenticationContextConfigurationClient configurationClient = AUTH_CONFIGURATION_CLIENT;
                        UnaryOperator<SaslClientFactory> factoryOperator = factory -> new ServerNameSaslClientFactory(factory, remoteServerName);
                        factoryOperator = and(ClientConnectionOpenListener.this.saslClientFactoryOperator, factoryOperator);
                        final SaslClient saslClient;
                        final SslChannel sslChannel = connection.getSslChannel();
                        final SSLSession sslSession;
                        if (sslChannel != null) {
                            sslSession = sslChannel.getSslSession();
                        } else {
                            sslSession = null;
                        }
                        try {
                            saslClient = configurationClient.createSaslClient(uri, ClientConnectionOpenListener.this.configuration, serverSaslMechs, factoryOperator, sslSession);
                        } catch (SaslException e) {
                            // apparently no more mechanisms can succeed
                            connection.handleException(e);
                            return;
                        }
                        if (saslClient == null) {
                            if (failedMechs.isEmpty()) {
                                StringBuilder b = new StringBuilder();
                                b.append("Authentication failed: none of the mechanisms presented by the server (");
                                final Iterator<String> iterator = serverSaslMechs.iterator();
                                if (iterator.hasNext()) {
                                    String mech = iterator.next();
                                    b.append(mech);
                                    while (iterator.hasNext()) {
                                        mech = iterator.next();
                                        b.append(',').append(' ').append(mech);
                                    }
                                }
                                b.append(") are supported");
                                connection.handleException(new SaslException(b.toString()));
                            } else {
                                connection.handleException(allMechanismsFailed());
                            }
                            return;
                        }
                        final String mechanismName = saslClient.getMechanismName();
                        client.tracef("Client initiating authentication using mechanism %s", mechanismName);

                        connection.getMessageReader().suspendReads();
                        final int negotiatedVersion = version;
                        final SaslClient usedSaslClient = saslClient;
                        final Authentication authentication = new Authentication(usedSaslClient, remoteServerName, remoteEndpointName, behavior, channelsIn, channelsOut, authCap, offeredMechs);
                        connection.getExecutor().execute(() -> {
                            final byte[] response;
                            try {
                                response = usedSaslClient.hasInitialResponse() ? usedSaslClient.evaluateChallenge(EMPTY_BYTES) : null;
                            } catch (SaslException e) {
                                client.tracef("Client authentication failed: %s", e);
                                saslDispose(usedSaslClient);
                                failedMechs.put(mechanismName, e);
                                sendCapRequest(remoteServerName);
                                return;
                            }
                            // Prepare the request message body
                            final Pooled<ByteBuffer> pooledSendBuffer = connection.allocate();
                            boolean ok = false;
                            try {
                                final ByteBuffer sendBuffer = pooledSendBuffer.getResource();
                                sendBuffer.put(Protocol.AUTH_REQUEST);
                                if (negotiatedVersion < 1) {
                                    sendBuffer.put(mechanismName.getBytes(StandardCharsets.UTF_8));
                                } else {
                                    ProtocolUtils.writeString(sendBuffer, mechanismName);
                                    if (response != null) {
                                        sendBuffer.put(response);
                                    }
                                }

                                sendBuffer.flip();
                                connection.send(pooledSendBuffer);
                                ok = true;
                                connection.setReadListener(authentication, true);
                                return;
                            } finally {
                                if (! ok) pooledSendBuffer.free();
                            }
                        });
                        return;
                    }
                    default: {
                        client.unknownProtocolId(msgType);
                        connection.handleException(client.invalidMessage(connection));
                        return;
                    }
                }
            } catch (BufferUnderflowException | BufferOverflowException e) {
                connection.handleException(client.invalidMessage(connection));
                return;
            } finally {
                message.free();
            }
        }
    }

    final class StartTls implements ChannelListener<ConduitStreamSourceChannel> {

        private final String remoteServerName;

        StartTls(final String remoteServerName) {
            this.remoteServerName = remoteServerName;
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
                connection.handleException(client.abruptClose(connection));
                return;
            }
            if (message == null) {
                return;
            }
            try {
                final ByteBuffer receiveBuffer = message.getResource();
                client.tracef("Received %s", receiveBuffer);
                final byte msgType = receiveBuffer.get();
                switch (msgType) {
                    case Protocol.CONNECTION_ALIVE: {
                        client.trace("Client received connection alive");
                        connection.sendAliveResponse();
                        return;
                    }
                    case Protocol.CONNECTION_ALIVE_ACK: {
                        client.trace("Client received connection alive ack");
                        return;
                    }
                    case Protocol.CONNECTION_CLOSE: {
                        client.trace("Client received connection close request");
                        connection.handlePreAuthCloseRequest();
                        return;
                    }
                    case Protocol.STARTTLS: {
                        client.trace("Client received STARTTLS response");
                        if (connection.getConnection() instanceof SslConnection) {
                            connection.send(RemoteConnection.STARTTLS_SENTINEL);
                            sendCapRequest(remoteServerName);
                            return;
                        } else {
                            // this should never happen
                            connection.handleException(new IOException("Client starting STARTTLS but channel doesn't support SSL"));
                            return;
                        }
                    }
                    default: {
                        client.unknownProtocolId(msgType);
                        connection.handleException(client.invalidMessage(connection));
                        return;
                    }
                }
            } catch (BufferUnderflowException | BufferOverflowException e) {
                connection.handleException(client.invalidMessage(connection));
                return;
            } finally {
                message.free();
            }
        }
    }

    final class Authentication implements ChannelListener<ConduitStreamSourceChannel> {

        private final SaslClient saslClient;
        private final String serverName;
        private final String remoteEndpointName;
        private final int behavior;
        private final int maxInboundChannels;
        private final int maxOutboundChannels;
        private final boolean authCap;
        private final Set<String> offeredMechanisms;

        Authentication(final SaslClient saslClient, final String serverName, final String endpointName, final int behavior, final int maxInboundChannels, final int maxOutboundChannels, final boolean authCap, final Set<String> offeredMechanisms) {
            this.saslClient = saslClient;
            this.serverName = serverName;
            this.behavior = behavior;
            this.remoteEndpointName = endpointName;
            this.maxInboundChannels = maxInboundChannels;
            this.maxOutboundChannels = maxOutboundChannels;
            this.authCap = authCap;
            this.offeredMechanisms = offeredMechanisms;
        }

        public void handleEvent(final ConduitStreamSourceChannel channel) {
            final Pooled<ByteBuffer> message;
            final MessageReader messageReader = connection.getMessageReader();
            try {
                message = messageReader.getMessage();
            } catch (IOException e) {
                connection.handleException(e);
                return;
            }
            if (message == MessageReader.EOF_MARKER) {
                connection.handleException(client.abruptClose(connection));
                saslDispose(saslClient);
                return;
            }
            if (message == null) {
                return;
            }
            boolean free = true;
            try {
                final ByteBuffer buffer = message.getResource();
                final byte msgType = buffer.get();
                switch (msgType) {
                    case Protocol.CONNECTION_ALIVE: {
                        client.trace("Client received connection alive");
                        connection.sendAliveResponse();
                        return;
                    }
                    case Protocol.CONNECTION_ALIVE_ACK: {
                        client.trace("Client received connection alive ack");
                        return;
                    }
                    case Protocol.CONNECTION_CLOSE: {
                        client.trace("Client received connection close request");
                        connection.handlePreAuthCloseRequest();
                        saslDispose(saslClient);
                        return;
                    }
                    case Protocol.AUTH_CHALLENGE: {
                        client.trace("Client received authentication challenge");
                        messageReader.suspendReads();
                        connection.getExecutor().execute(() -> {
                            try {
                                final boolean clientComplete = saslClient.isComplete();
                                if (clientComplete) {
                                    connection.handleException(new SaslException(saslClient.getMechanismName() + ": Received extra auth message after completion"));
                                    return;
                                }
                                final byte[] response;
                                final byte[] challenge = Buffers.take(buffer, buffer.remaining());
                                try {
                                    response = saslClient.evaluateChallenge(challenge);
                                } catch (Throwable e) {
                                    final String mechanismName = saslClient.getMechanismName();
                                    client.debugf("Client authentication failed for mechanism %s: %s", mechanismName, e);
                                    failedMechs.put(mechanismName, e);
                                    saslDispose(saslClient);
                                    sendCapRequest(serverName);
                                    return;
                                }
                                client.trace("Client sending authentication response");
                                final Pooled<ByteBuffer> pooled = connection.allocate();
                                boolean ok = false;
                                try {
                                    final ByteBuffer sendBuffer = pooled.getResource();
                                    sendBuffer.put(Protocol.AUTH_RESPONSE);
                                    sendBuffer.put(response);
                                    sendBuffer.flip();
                                    connection.send(pooled);
                                    ok = true;
                                    messageReader.resumeReads();
                                } finally {
                                    if (! ok) pooled.free();
                                }
                                return;
                            } finally {
                                message.free();
                            }
                        });
                        free = false;
                        return;
                    }
                    case Protocol.AUTH_COMPLETE: {
                        client.trace("Client received authentication complete");
                        messageReader.suspendReads();
                        connection.getExecutor().execute(() -> {
                            try {
                                final boolean clientComplete = saslClient.isComplete();
                                final byte[] challenge = Buffers.take(buffer, buffer.remaining());
                                if (!clientComplete) try {
                                    final byte[] response = saslClient.evaluateChallenge(challenge);
                                    if (response != null && response.length > 0) {
                                        connection.handleException(new SaslException(saslClient.getMechanismName() + ": Received extra auth message after completion"));
                                        saslDispose(saslClient);
                                        return;
                                    }
                                    if (!saslClient.isComplete()) {
                                        connection.handleException(new SaslException(saslClient.getMechanismName() + ": Client not complete after processing auth complete message"));
                                        saslDispose(saslClient);
                                        return;
                                    }
                                } catch (Throwable e) {
                                    final String mechanismName = saslClient.getMechanismName();
                                    client.debugf("Client authentication failed for mechanism %s: %s", mechanismName, e);
                                    failedMechs.put(mechanismName, e);
                                    saslDispose(saslClient);
                                    sendCapRequest(serverName);
                                    return;
                                }
                                final Object qop = saslClient.getNegotiatedProperty(Sasl.QOP);
                                if ("auth-int".equals(qop) || "auth-conf".equals(qop)) {
                                    connection.setSaslWrapper(SaslWrapper.create(saslClient));
                                }
                                final Object principalObj = saslClient.getNegotiatedProperty(WildFlySasl.PRINCIPAL);
                                // auth complete.
                                final ConnectionHandlerFactory connectionHandlerFactory = connectionContext -> {

                                    // this happens immediately.
                                    final String hostName = connection.getLocalAddress().getHostName();
                                    final RemoteConnectionHandler connectionHandler = new RemoteConnectionHandler(connectionContext, connection, maxInboundChannels, maxOutboundChannels, principalObj instanceof Principal ? (Principal) principalObj : AnonymousPrincipal.getInstance(), remoteEndpointName, behavior, authCap, offeredMechanisms, serverName, hostName);
                                    connection.setReadListener(new RemoteReadListener(connectionHandler, connection), false);
                                    connection.getRemoteConnectionProvider().addConnectionHandler(connectionHandler);
                                    return connectionHandler;
                                };
                                connection.getResult().setResult(connectionHandlerFactory);
                                messageReader.resumeReads();
                                return;
                            } finally {
                                message.free();
                            }
                        });
                        free = false;
                        return;
                    }
                    case Protocol.AUTH_REJECTED: {
                        final String mechanismName = saslClient.getMechanismName();
                        client.debugf("Client received authentication rejected for mechanism %s", mechanismName);
                        failedMechs.put(mechanismName, new SaslException(mechanismName + ": Server rejected authentication"));
                        saslDispose(saslClient);
                        sendCapRequest(serverName);
                        return;
                    }
                    default: {
                        client.unknownProtocolId(msgType);
                        connection.handleException(client.invalidMessage(connection));
                        saslDispose(saslClient);
                        return;
                    }
                }
            } finally {
                if (free) message.free();
            }
        }
    }

    private static <T> UnaryOperator<T> and(final UnaryOperator<T> first, final UnaryOperator<T> second) {
        return t -> second.apply(first.apply(t));
    }
}
