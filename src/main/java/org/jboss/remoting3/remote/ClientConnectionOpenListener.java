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

package org.jboss.remoting3.remote;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pooled;
import org.xnio.Sequence;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.SslChannel;
import org.xnio.sasl.SaslUtils;
import org.xnio.sasl.SaslWrapper;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import static org.jboss.remoting3.remote.RemoteLogger.client;
import static org.xnio.sasl.SaslUtils.EMPTY_BYTES;

final class ClientConnectionOpenListener implements ChannelListener<ConnectedMessageChannel> {
    private final RemoteConnection connection;
    private final ConnectionProviderContext connectionProviderContext;
    private final CallbackHandler callbackHandler;
    private final AccessControlContext accessControlContext;
    private final OptionMap optionMap;
    private final Set<String> failedMechs = new HashSet<String>();
    private final Set<String> allowedMechs;
    private final Set<String> disallowedMechs;

    ClientConnectionOpenListener(final RemoteConnection connection, final ConnectionProviderContext connectionProviderContext, final CallbackHandler callbackHandler, final AccessControlContext accessControlContext, final OptionMap optionMap) {
        this.connection = connection;
        this.connectionProviderContext = connectionProviderContext;
        this.callbackHandler = callbackHandler;
        this.accessControlContext = accessControlContext;
        this.optionMap = optionMap;
        final Sequence<String> allowedMechs = optionMap.get(Options.SASL_MECHANISMS);
        final Sequence<String> disallowedMechs = optionMap.get(Options.SASL_DISALLOWED_MECHANISMS);
        this.allowedMechs = allowedMechs == null ? null : new HashSet<String>(allowedMechs);
        this.disallowedMechs = disallowedMechs == null ? Collections.<String>emptySet() : new HashSet<String>(disallowedMechs);
    }

    public void handleEvent(final ConnectedMessageChannel channel) {
        connection.setReadListener(new Greeting());
    }

    void sendCapRequest(final String remoteServerName) {
        client.trace("Client sending capabilities request");
        // Prepare the request message body
        final Pooled<ByteBuffer> pooledSendBuffer = connection.allocate();
        boolean ok = false;
        try {
            final ByteBuffer sendBuffer = pooledSendBuffer.getResource();
            sendBuffer.put(Protocol.CAPABILITIES);
            ProtocolUtils.writeByte(sendBuffer, Protocol.CAP_VERSION, 0);
            final String localEndpointName = connectionProviderContext.getEndpoint().getName();
            if (localEndpointName != null) {
                ProtocolUtils.writeString(sendBuffer, Protocol.CAP_ENDPOINT_NAME, localEndpointName);
            }
            sendBuffer.flip();
            connection.setReadListener(new Capabilities(remoteServerName));
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

    final class Greeting implements ChannelListener<ConnectedMessageChannel> {

        public void handleEvent(final ConnectedMessageChannel channel) {
            final Pooled<ByteBuffer> pooledReceiveBuffer = connection.allocate();
            try {
                final ByteBuffer receiveBuffer = pooledReceiveBuffer.getResource();
                int res;
                try {
                    res = channel.receive(receiveBuffer);
                } catch (IOException e) {
                    connection.handleException(e);
                    return;
                }
                if (res == -1) {
                    connection.handleException(client.abruptClose(connection));
                    return;
                }
                if (res == 0) {
                    return;
                }
                client.tracef("Received %s", receiveBuffer);
                receiveBuffer.flip();
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
                            remoteServerName = channel.getPeerAddress(InetSocketAddress.class).getHostName();
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
            } catch (BufferUnderflowException e) {
                connection.handleException(client.invalidMessage(connection));
                return;
            } catch (BufferOverflowException e) {
                connection.handleException(client.invalidMessage(connection));
                return;
            } finally {
                pooledReceiveBuffer.free();
            }
        }

    }

    final class Capabilities implements ChannelListener<ConnectedMessageChannel> {

        private final String remoteServerName;

        Capabilities(final String remoteServerName) {
            this.remoteServerName = remoteServerName;
        }

        public void handleEvent(final ConnectedMessageChannel channel) {
            final Pooled<ByteBuffer> pooledReceiveBuffer = connection.allocate();
            try {
                final ByteBuffer receiveBuffer = pooledReceiveBuffer.getResource();
                int res;
                try {
                    res = channel.receive(receiveBuffer);
                } catch (IOException e) {
                    connection.handleException(e);
                    return;
                }
                if (res == -1) {
                    connection.handleException(client.abruptClose(connection));
                    return;
                }
                if (res == 0) {
                    return;
                }
                receiveBuffer.flip();
                boolean starttls = false;
                final Set<String> saslMechs = new LinkedHashSet<String>();
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
                        while (receiveBuffer.hasRemaining()) {
                            final byte type = receiveBuffer.get();
                            final int len = receiveBuffer.get() & 0xff;
                            final ByteBuffer data = Buffers.slice(receiveBuffer, len);
                            switch (type) {
                                case Protocol.CAP_VERSION: {
                                    final byte version = data.get();
                                    client.tracef("Client received capability: version %d", Integer.valueOf(version & 0xff));
                                    // We only support version zero, so knowing the other side's version is not useful presently
                                    break;
                                }
                                case Protocol.CAP_SASL_MECH: {
                                    final String mechName = Buffers.getModifiedUtf8(data);
                                    client.tracef("Client received capability: SASL mechanism %s", mechName);
                                    if (! failedMechs.contains(mechName) && ! disallowedMechs.contains(mechName) && (allowedMechs == null || allowedMechs.contains(mechName))) {
                                        client.tracef("SASL mechanism %s added to allowed set", mechName);
                                        saslMechs.add(mechName);
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
                                default: {
                                    client.tracef("Client received unknown capability %02x", Integer.valueOf(type & 0xff));
                                    // unknown, skip it for forward compatibility.
                                    break;
                                }
                            }
                        }
                        if (starttls) {
                            // only initiate starttls if not forbidden by config
                            if (optionMap.get(Options.SSL_STARTTLS, true)) {
                                // Prepare the request message body
                                final Pooled<ByteBuffer> pooledSendBuffer = connection.allocate();
                                final ByteBuffer sendBuffer = pooledSendBuffer.getResource();
                                sendBuffer.put(Protocol.STARTTLS);
                                sendBuffer.flip();
                                connection.setReadListener(new StartTls(remoteServerName));
                                connection.send(pooledSendBuffer);
                                // all set
                                return;
                            }
                        }

                        if (saslMechs.isEmpty()) {
                            connection.handleException(new SaslException("Authentication failed: No authentication mechanisms are available"));
                            return;
                        }
                        // OK now send our authentication request
                        final OptionMap optionMap = connection.getOptionMap();
                        final String userName = optionMap.get(RemotingOptions.AUTHORIZE_ID);
                        final Map<String, ?> propertyMap = SaslUtils.createPropertyMap(optionMap, Channels.getOption(channel, Options.SECURE, false));
                        final SaslClient saslClient;
                        try {
                            saslClient = AccessController.doPrivileged(new PrivilegedExceptionAction<SaslClient>() {
                                public SaslClient run() throws SaslException {
                                    return Sasl.createSaslClient(saslMechs.toArray(new String[saslMechs.size()]), userName, "remote", remoteServerName, propertyMap, callbackHandler);
                                }
                            }, accessControlContext);
                        } catch (PrivilegedActionException e) {
                            final SaslException se = (SaslException) e.getCause();
                            connection.handleException(se);
                            return;
                        }
                        if (saslClient == null) {
                            connection.handleException(new SaslException("Authentication failed: all available authentication mechanisms failed"));
                            return;
                        }
                        final String mechanismName = saslClient.getMechanismName();
                        client.tracef("Client initiating authentication using mechanism %s", mechanismName);

                        final String theRemoteEndpointName = remoteEndpointName;
                        connection.getChannel().suspendReads();
                        connection.getExecutor().execute(new Runnable() {
                            public void run() {
                                byte[] response;
                                try {
                                    response = saslClient.hasInitialResponse() ? saslClient.evaluateChallenge(EMPTY_BYTES)
                                            : null;

                                } catch (Exception e) {
                                    client.tracef("Client authentication failed: %s", e);
                                    failedMechs.add(mechanismName);
                                    sendCapRequest(remoteServerName);
                                    return;
                                }
                                // Prepare the request message body
                                final Pooled<ByteBuffer> pooledSendBuffer = connection.allocate();
                                final ByteBuffer sendBuffer = pooledSendBuffer.getResource();
                                sendBuffer.put(Protocol.AUTH_REQUEST);
                                ProtocolUtils.writeString(sendBuffer, mechanismName);
                                if (response != null) {
                                    sendBuffer.put(response);
                                }

                                sendBuffer.flip();
                                connection.setReadListener(new Authentication(saslClient, remoteServerName, userName,
                                        theRemoteEndpointName));
                                connection.send(pooledSendBuffer);
                                connection.getChannel().resumeReads();
                                return;
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
            } catch (BufferUnderflowException e) {
                connection.handleException(client.invalidMessage(connection));
                return;
            } catch (BufferOverflowException e) {
                connection.handleException(client.invalidMessage(connection));
                return;
            } finally {
                pooledReceiveBuffer.free();
            }
        }
    }

    final class StartTls implements ChannelListener<ConnectedMessageChannel> {

        private final String remoteServerName;

        StartTls(final String remoteServerName) {
            this.remoteServerName = remoteServerName;
        }

        public void handleEvent(final ConnectedMessageChannel channel) {
            final Pooled<ByteBuffer> pooledReceiveBuffer = connection.allocate();
            try {
                final ByteBuffer receiveBuffer = pooledReceiveBuffer.getResource();
                int res;
                try {
                    res = channel.receive(receiveBuffer);
                } catch (IOException e) {
                    connection.handleException(e);
                    return;
                }
                if (res == -1) {
                    connection.handleException(client.abruptClose(connection));
                    return;
                }
                if (res == 0) {
                    return;
                }
                client.tracef("Received %s", receiveBuffer);
                receiveBuffer.flip();
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
                        try {
                            ((SslChannel)channel).startHandshake();
                        } catch (IOException e) {
                            connection.handleException(e, false);
                            return;
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
            } catch (BufferUnderflowException e) {
                connection.handleException(client.invalidMessage(connection));
                return;
            } catch (BufferOverflowException e) {
                connection.handleException(client.invalidMessage(connection));
                return;
            } finally {
                pooledReceiveBuffer.free();
            }
        }
    }

    final class Authentication implements ChannelListener<ConnectedMessageChannel> {

        private final SaslClient saslClient;
        private final String serverName;
        private final String authorizationID;
        private final String remoteEndpointName;

        Authentication(final SaslClient saslClient, final String serverName, final String authorizationID, final String remoteEndpointName) {
            this.saslClient = saslClient;
            this.serverName = serverName;
            this.authorizationID = authorizationID;
            this.remoteEndpointName = remoteEndpointName;
        }

        public void handleEvent(final ConnectedMessageChannel channel) {
            final Pooled<ByteBuffer> pooledBuffer = connection.allocate();
            try {
                final ByteBuffer buffer = pooledBuffer.getResource();
                final int res;
                try {
                    res = channel.receive(buffer);
                } catch (IOException e) {
                    connection.handleException(e);
                    return;
                }
                if (res == 0) {
                    return;
                }
                if (res == -1) {
                    connection.handleException(client.abruptClose(connection));
                    return;
                }
                buffer.flip();
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
                        return;
                    }
                    case Protocol.AUTH_CHALLENGE: {
                        client.trace("Client received authentication challenge");
                        connection.getChannel().suspendReads();
                        connection.getExecutor().execute(new Runnable() {
                            public void run() {
                                final boolean clientComplete = saslClient.isComplete();
                                if (clientComplete) {
                                    connection.handleException(new SaslException("Received extra auth message after completion"));
                                    return;
                                }
                                final byte[] response;
                                final byte[] challenge = Buffers.take(buffer, buffer.remaining());
                                try {
                                    response = saslClient.evaluateChallenge(challenge);
                                    if (msgType == Protocol.AUTH_COMPLETE && response != null && response.length > 0) {
                                        connection.handleException(new SaslException("Received extra auth message after completion"));
                                        return;
                                    }
                                } catch (Throwable e) {
                                    final String mechanismName = saslClient.getMechanismName();
                                    client.debugf("Client authentication failed for mechanism %s: %s", mechanismName, e);
                                    failedMechs.add(mechanismName);
                                    sendCapRequest(serverName);
                                    connection.getChannel().resumeReads();
                                    return;
                                }
                                client.trace("Client sending authentication response");
                                final Pooled<ByteBuffer> pooled = connection.allocate();
                                final ByteBuffer sendBuffer = pooled.getResource();
                                sendBuffer.put(Protocol.AUTH_RESPONSE);
                                sendBuffer.put(response);
                                sendBuffer.flip();
                                connection.send(pooled);
                                connection.getChannel().resumeReads();
                                return;
                            }
                        });
                        return;
                    }
                    case Protocol.AUTH_COMPLETE: {
                        client.trace("Client received authentication complete");
                        connection.getChannel().suspendReads();
                        connection.getExecutor().execute(new Runnable() {
                            public void run() {
                                final boolean clientComplete = saslClient.isComplete();
                                final byte[] challenge = Buffers.take(buffer, buffer.remaining());
                                if (!clientComplete) try {
                                    final byte[] response = saslClient.evaluateChallenge(challenge);
                                    if (response != null && response.length > 0) {
                                        connection.handleException(new SaslException("Received extra auth message after completion"));
                                        return;
                                    }
                                    if (!saslClient.isComplete()) {
                                        connection.handleException(new SaslException("Client not complete after processing auth complete message"));
                                        return;
                                    }
                                } catch (Throwable e) {
                                    final String mechanismName = saslClient.getMechanismName();
                                    client.debugf("Client authentication failed for mechanism %s: %s", mechanismName, e);
                                    failedMechs.add(mechanismName);
                                    sendCapRequest(serverName);
                                    return;
                                }
                                final Object qop = saslClient.getNegotiatedProperty(Sasl.QOP);
                                if ("auth-int".equals(qop) || "auth-conf".equals(qop)) {
                                    connection.setSaslWrapper(SaslWrapper.create(saslClient));
                                }
                                // auth complete.
                                final ConnectionHandlerFactory connectionHandlerFactory = new ConnectionHandlerFactory() {
                                    public ConnectionHandler createInstance(final ConnectionHandlerContext connectionContext) {
                                        // this happens immediately.
                                        final RemoteConnectionHandler connectionHandler = new RemoteConnectionHandler(connectionContext, connection, authorizationID, remoteEndpointName);
                                        connection.setReadListener(new RemoteReadListener(connectionHandler, connection));
                                        return connectionHandler;
                                    }
                                };
                                connection.getResult().setResult(connectionHandlerFactory);
                                connection.getChannel().resumeReads();
                                return;
                            }
                        });
                        return;
                    }
                    case Protocol.AUTH_REJECTED: {
                        final String mechanismName = saslClient.getMechanismName();
                        client.debugf("Client received authentication rejected for mechanism %s", mechanismName);
                        failedMechs.add(mechanismName);
                        sendCapRequest(serverName);
                        return;
                    }
                    default: {
                        client.unknownProtocolId(msgType);
                        connection.handleException(client.invalidMessage(connection));
                        return;
                    }
                }
            } finally {
                pooledBuffer.free();
            }
        }
    }
}
