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
import java.nio.channels.Channel;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.Version;
import org.jboss.remoting3.security.InetAddressPrincipal;
import org.jboss.remoting3.security.SimpleUserInfo;
import org.jboss.remoting3.security.UserPrincipal;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.xnio.BufferAllocator;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pooled;
import org.xnio.Sequence;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.SslChannel;
import org.xnio.channels.WrappedChannel;
import org.xnio.sasl.SaslUtils;
import org.xnio.sasl.SaslWrapper;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslException;

import static org.jboss.remoting3.remote.RemoteLogger.client;
import static org.xnio.sasl.SaslUtils.EMPTY_BYTES;

final class ClientConnectionOpenListener implements ChannelListener<ConnectedMessageChannel> {
    private final RemoteConnection connection;
    private final ConnectionProviderContext connectionProviderContext;
    private final CallbackHandler callbackHandler;
    private final AccessControlContext accessControlContext;
    private final OptionMap optionMap;
    private final Map<String, String> failedMechs = new LinkedHashMap<String, String>();
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
        connection.setReadListener(new Greeting(), true);
    }

    SaslException allMechanismsFailed() {
        final StringBuilder b = new StringBuilder();
        b.append("Authentication failed: all available authentication mechanisms failed:");
        for (Map.Entry<String, String> entry : failedMechs.entrySet()) {
            final String key = entry.getKey();
            final String value = entry.getValue();
            b.append("\n   ").append(key).append(": ").append(value);
        }
        return new SaslException(b.toString());
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
            sendBuffer.flip();
            connection.setReadListener(new Capabilities(remoteServerName), true);
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

    final class Greeting implements ChannelListener<ConnectedMessageChannel> {

        public void handleEvent(final ConnectedMessageChannel channel) {
            final Pooled<ByteBuffer> pooledReceiveBuffer = connection.allocate();
            try {
                final ByteBuffer receiveBuffer = pooledReceiveBuffer.getResource();
                synchronized (connection.getLock()) {
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
            Pooled<ByteBuffer> pooledReceiveBuffer = connection.allocate();
            ByteBuffer receiveBuffer;
            try {
                if (channel instanceof RemotingMessageChannel) {
                    synchronized (connection.getLock()) {
                        int res;
                        RemotingMessageChannel.AdjustedBuffer ab = new RemotingMessageChannel.AdjustedBuffer(pooledReceiveBuffer);
                        try {
                            RemotingMessageChannel rc = (RemotingMessageChannel) channel;
                            res = rc.receive(ab);
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
                        pooledReceiveBuffer = ab.getAdjustedBuffer();
                        receiveBuffer = pooledReceiveBuffer.getResource();
                    }
                } else {
                    receiveBuffer = pooledReceiveBuffer.getResource();
                    synchronized (connection.getLock()) {
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
                    }
                }
                receiveBuffer.flip();
                boolean starttls = false;
                final Set<String> serverSaslMechs = new LinkedHashSet<String>();
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
                            // only initiate starttls if not forbidden by config
                            if (optionMap.get(Options.SSL_STARTTLS, true)) {
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
                            connection.handleException(new SaslException("Authentication failed: the server presented no authentication mechanisms"));
                            return;
                        }

                        final List<String> saslMechs = new ArrayList<String>(serverSaslMechs.size());
                        if (allowedMechs != null) {
                            saslMechs.addAll(allowedMechs);
                            saslMechs.retainAll(serverSaslMechs);
                        } else {
                            saslMechs.addAll(serverSaslMechs);
                        }

                        // OK now send our authentication request
                        final OptionMap optionMap = connection.getOptionMap();
                        final String userName = optionMap.get(RemotingOptions.AUTHORIZE_ID);
                        final Map<String, ?> propertyMap = SaslUtils.createPropertyMap(optionMap, Channels.getOption(channel, Options.SECURE, false));
                        SaslClient saslClient = null;
                        final Iterator<SaslClientFactory> iterator = AccessController.doPrivileged(new PrivilegedAction<Iterator<SaslClientFactory>>() {
                            public Iterator<SaslClientFactory> run() {
                                return SaslUtils.getSaslClientFactories(getClass().getClassLoader(), true);
                            }
                        });
                        final LinkedHashMap<String, Set<SaslClientFactory>> factories = new LinkedHashMap<String, Set<SaslClientFactory>>();
                        while (iterator.hasNext()) {
                            final SaslClientFactory factory = iterator.next();
                            for (String name : factory.getMechanismNames(propertyMap)) {
                                if (! factories.containsKey(name)) {
                                    factories.put(name, new LinkedHashSet<SaslClientFactory>(Collections.singleton(factory)));
                                } else {
                                    factories.get(name).add(factory);
                                }
                            }
                        }
                        FOUND: for (String mechanism : saslMechs) {
                            final Set<SaslClientFactory> factorySet = factories.get(mechanism);
                            final String protocol = optionMap.contains(RemotingOptions.SASL_PROTOCOL) ? optionMap.get(RemotingOptions.SASL_PROTOCOL) : RemotingOptions.DEFAULT_SASL_PROTOCOL;
                            // By default we allow the remote server to tell us it's name - config can mandate a specific server name is expected.
                            final String remoteServerName = optionMap.contains(RemotingOptions.SERVER_NAME) ? optionMap.get(RemotingOptions.SERVER_NAME) : this.remoteServerName;
                            if (factorySet == null) continue;
                            final String[] strings = new String[] { mechanism };
                            for (final SaslClientFactory factory : factorySet) {
                                try {
                                    saslClient = AccessController.doPrivileged(new PrivilegedExceptionAction<SaslClient>() {
                                        public SaslClient run() throws SaslException {
                                            return factory.createSaslClient(strings, userName, protocol, remoteServerName, propertyMap, callbackHandler);
                                        }
                                    }, accessControlContext);
                                } catch (PrivilegedActionException e) {
                                    // in case this is the last one...
                                    failedMechs.put(mechanism, e.getCause().toString());
                                }
                                if (saslClient != null) {
                                    failedMechs.remove(mechanism);
                                    break FOUND;
                                }
                            }
                            failedMechs.put(mechanism, "No implementation found");
                        }
                        if (saslClient == null) {
                            connection.handleException(allMechanismsFailed());
                            return;
                        }
                        final String mechanismName = saslClient.getMechanismName();
                        client.tracef("Client initiating authentication using mechanism %s", mechanismName);

                        final String theRemoteEndpointName = remoteEndpointName;
                        connection.getChannel().suspendReads();
                        final int negotiatedVersion = version;
                        final SaslClient usedSaslClient = saslClient;
                        final Authentication authentication = new Authentication(usedSaslClient, remoteServerName, userName, theRemoteEndpointName, behavior, channelsIn, channelsOut);
                        connection.getExecutor().execute(new Runnable() {
                            public void run() {
                                final byte[] response;
                                try {
                                    if (usedSaslClient.hasInitialResponse()) {
                                        response = AccessController.doPrivileged(new PrivilegedExceptionAction<byte[]>() {

                                            @Override
                                            public byte[] run() throws Exception {
                                                return usedSaslClient.evaluateChallenge(EMPTY_BYTES);
                                            }
                                        }, accessControlContext);
                                    } else {
                                        response = null;
                                    }
                                } catch (PrivilegedActionException e) {
                                    client.tracef("Client authentication failed: %s", e.getCause());
                                    saslDispose(usedSaslClient);
                                    failedMechs.put(mechanismName, e.getCause().toString());
                                    sendCapRequest(remoteServerName);
                                    return;
                                }
                                // Prepare the request message body
                                Pooled<ByteBuffer> pooledSendBuffer = connection.allocate();

                                if (response != null && response.length > pooledSendBuffer.getResource().capacity()) {
                                    pooledSendBuffer = Buffers.allocatedBufferPool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, response.length + 100).allocate();
                                    connection.adjustToMessageLength(response.length + 100);
                                }

                                boolean ok = false;
                                try {
                                    final ByteBuffer sendBuffer = pooledSendBuffer.getResource();
                                    sendBuffer.put(Protocol.AUTH_REQUEST);
                                    if (negotiatedVersion < 1) {
                                        sendBuffer.put(mechanismName.getBytes(Protocol.UTF_8));
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
                synchronized (connection.getLock()) {
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
                        Channel c = channel;
                        for (;;) {
                            if (c instanceof SslChannel) {
                                try {
                                    ((SslChannel)c).startHandshake();
                                } catch (IOException e) {
                                    connection.handleException(e, false);
                                    return;
                                }
                                sendCapRequest(remoteServerName);
                                return;
                            } else if (c instanceof WrappedChannel) {
                                c = ((WrappedChannel<?>)c).getChannel();
                            } else {
                                // this should never happen
                                connection.handleException(new IOException("Client starting STARTTLS but channel doesn't support SSL"));
                                return;
                            }
                        }
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
        private final int behavior;
        private final int maxInboundChannels;
        private final int maxOutboundChannels;

        Authentication(final SaslClient saslClient, final String serverName, final String authorizationID, final String remoteEndpointName, final int behavior, final int maxInboundChannels, final int maxOutboundChannels) {
            this.saslClient = saslClient;
            this.serverName = serverName;
            this.authorizationID = authorizationID;
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
                synchronized (connection.getLock()) {
                    final int res;
                    try {
                        res = channel.receive(buffer);
                    } catch (IOException e) {
                        connection.handleException(e);
                        saslDispose(saslClient);
                        return;
                    }
                    if (res == 0) {
                        return;
                    }
                    if (res == -1) {
                        connection.handleException(client.abruptClose(connection));
                        saslDispose(saslClient);
                        return;
                    }
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
                        saslDispose(saslClient);
                        return;
                    }
                    case Protocol.AUTH_CHALLENGE: {
                        client.trace("Client received authentication challenge");
                        channel.suspendReads();
                        connection.getExecutor().execute(new Runnable() {
                            public void run() {
                                try {
                                    final boolean clientComplete = saslClient.isComplete();
                                    if (clientComplete) {
                                        connection.handleException(new SaslException("Received extra auth message after completion"));
                                        return;
                                    }
                                    final byte[] response;
                                    final byte[] challenge = Buffers.take(buffer, buffer.remaining());
                                    try {
                                        response = saslClient.evaluateChallenge(challenge);
                                    } catch (Throwable e) {
                                        final String mechanismName = saslClient.getMechanismName();
                                        client.debugf("Client authentication failed for mechanism %s: %s", mechanismName, e);
                                        failedMechs.put(mechanismName, e.toString());
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
                                        channel.resumeReads();
                                    } finally {
                                        if (! ok) pooled.free();
                                    }
                                    return;
                                } finally {
                                    pooledBuffer.free();
                                }
                            }
                        });
                        free = false;
                        return;
                    }
                    case Protocol.AUTH_COMPLETE: {
                        client.trace("Client received authentication complete");
                        channel.suspendReads();
                        connection.getExecutor().execute(new Runnable() {
                            public void run() {
                                try {
                                    final boolean clientComplete = saslClient.isComplete();
                                    final byte[] challenge = Buffers.take(buffer, buffer.remaining());
                                    if (!clientComplete) try {
                                        final byte[] response = saslClient.evaluateChallenge(challenge);
                                        if (response != null && response.length > 0) {
                                            connection.handleException(new SaslException("Received extra auth message after completion"));
                                            saslDispose(saslClient);
                                            return;
                                        }
                                        if (!saslClient.isComplete()) {
                                            connection.handleException(new SaslException("Client not complete after processing auth complete message"));
                                            saslDispose(saslClient);
                                            return;
                                        }
                                    } catch (Throwable e) {
                                        final String mechanismName = saslClient.getMechanismName();
                                        client.debugf("Client authentication failed for mechanism %s: %s", mechanismName, e);
                                        failedMechs.put(mechanismName, e.toString());
                                        saslDispose(saslClient);
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
                                            Collection<Principal> principals = definePrincipals();

                                            // this happens immediately.
                                            final RemoteConnectionHandler connectionHandler = new RemoteConnectionHandler(connectionContext, connection, principals, new SimpleUserInfo(principals), maxInboundChannels, maxOutboundChannels, remoteEndpointName, behavior);
                                            connection.setReadListener(new RemoteReadListener(connectionHandler, connection), false);
                                            connection.getRemoteConnectionProvider().addConnectionHandler(connectionHandler);
                                            return connectionHandler;
                                        }
                                    };
                                    connection.getResult().setResult(connectionHandlerFactory);
                                    channel.resumeReads();
                                    return;
                                } finally {
                                    pooledBuffer.free();
                                }
                            }
                        });
                        free = false;
                        return;
                    }
                    case Protocol.AUTH_REJECTED: {
                        final String mechanismName = saslClient.getMechanismName();
                        client.debugf("Client received authentication rejected for mechanism %s", mechanismName);
                        failedMechs.put(mechanismName, "Server rejected authentication");
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
                if (free) pooledBuffer.free();
            }
        }

        private Collection<Principal> definePrincipals() {

            final Set<Principal> principals = new LinkedHashSet<Principal>();

            final SslChannel sslChannel = connection.getSslChannel();
            if (sslChannel != null) {
                // It might be STARTTLS, in which case we can still opt out of SSL
                final SSLSession session = sslChannel.getSslSession();
                if (session != null) {
                    try {
                        principals.add(session.getPeerPrincipal());
                    } catch (SSLPeerUnverifiedException ignored) {
                    }
                }
            }
            if (authorizationID != null) {
                principals.add(new UserPrincipal(authorizationID));
            }
            final ConnectedMessageChannel channel = connection.getChannel();
            final InetSocketAddress address = channel.getPeerAddress(InetSocketAddress.class);
            if (address != null) {
                principals.add(new InetAddressPrincipal(address.getAddress()));
            }

            return principals;
        }

    }
}
