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
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

import org.jboss.remoting3.security.AuthorizingCallbackHandler;
import org.jboss.remoting3.security.InetAddressPrincipal;
import org.jboss.remoting3.security.ServerAuthenticationProvider;
import org.jboss.remoting3.security.UserInfo;
import org.jboss.remoting3.security.UserPrincipal;
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

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ServerConnectionOpenListener  implements ChannelListener<ConnectedMessageChannel> {
    private final RemoteConnection connection;
    private final ConnectionProviderContext connectionProviderContext;
    private final ServerAuthenticationProvider serverAuthenticationProvider;
    private final OptionMap optionMap;
    private final AccessControlContext accessControlContext;
    private final AtomicInteger retryCount = new AtomicInteger(8);
    private final String serverName;

    ServerConnectionOpenListener(final RemoteConnection connection, final ConnectionProviderContext connectionProviderContext, final ServerAuthenticationProvider serverAuthenticationProvider, final OptionMap optionMap, final AccessControlContext accessControlContext) {
        this.connection = connection;
        this.connectionProviderContext = connectionProviderContext;
        this.serverAuthenticationProvider = serverAuthenticationProvider;
        this.optionMap = optionMap;
        this.accessControlContext = accessControlContext;
        serverName = connection.getChannel().getLocalAddress(InetSocketAddress.class).getHostName();
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
        } catch (BufferUnderflowException e) {
            connection.handleException(RemoteLogger.log.invalidMessage(connection));
            return;
        } catch (BufferOverflowException e) {
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
        private Map<String, ?> propertyMap;
        private Map<String, SaslServerFactory> allowedMechanisms;
        private int version;
        private String remoteEndpointName;

        Initial() {
            // Calculate our capabilities
            version = Protocol.VERSION;
        }

        void initialiseCapabilities() {
            final SslChannel sslChannel = connection.getSslChannel();
            final boolean channelSecure = Channels.getOption(connection.getChannel(), Options.SECURE, false);
            starttls = ! (sslChannel == null || channelSecure);
            final Map<String, ?> propertyMap;
            final Map<String, SaslServerFactory> foundMechanisms = new LinkedHashMap<String, SaslServerFactory>();
            propertyMap = SaslUtils.createPropertyMap(optionMap, channelSecure);
            final Sequence<String> saslMechs = optionMap.get(Options.SASL_MECHANISMS);
            final Set<String> restrictions = saslMechs == null ? null : new LinkedHashSet<String>(saslMechs);
            final Sequence<String> saslNoMechs = optionMap.get(Options.SASL_DISALLOWED_MECHANISMS);
            final Set<String> disallowed = saslNoMechs == null ? Collections.<String>emptySet() : new HashSet<String>(saslNoMechs);
            final Iterator<SaslServerFactory> factories = SaslUtils.getSaslServerFactories(getClass().getClassLoader(), true);
            try {
                if ((restrictions == null || restrictions.contains("EXTERNAL")) && ! disallowed.contains("EXTERNAL")) {
                    // only enable external if there is indeed an external auth layer to be had
                    SSLSession sslSession;
                    if (sslChannel != null && (sslSession = sslChannel.getSslSession()) != null) {
                        final Principal principal = sslSession.getPeerPrincipal();
                        // only enable external auth if there's a peer principal (else it's just ANONYMOUS)
                        if (principal != null) {
                            foundMechanisms.put("EXTERNAL", new ExternalSaslServerFactory(principal));
                        } else {
                            server.trace("No EXTERNAL mechanism due to lack of peer principal");
                        }
                    } else {
                        server.trace("No EXTERNAL mechanism due to lack of SSL");
                    }
                } else {
                    server.trace("No EXTERNAL mechanism due to explicit exclusion");
                }
            } catch (IOException e) {
                // ignore
            }
            while (factories.hasNext()) {
                SaslServerFactory factory = factories.next();
                server.tracef("Trying SASL server factory %s", factory);
                for (String mechName : factory.getMechanismNames(propertyMap)) {
                    if (restrictions != null && ! restrictions.contains(mechName)) {
                        server.tracef("Excluding mechanism %s because it is not in the allowed list", mechName);
                    } else if (disallowed.contains(mechName)) {
                        server.tracef("Excluding mechanism %s because it is in the disallowed list", mechName);
                    } else if (foundMechanisms.containsKey(mechName)) {
                        server.tracef("Excluding repeated occurrence of mechanism %s", mechName);
                    } else {
                        server.tracef("Added mechanism %s", mechName);
                        foundMechanisms.put(mechName, factory);
                    }
                }
            }
            this.propertyMap = propertyMap;
            if (restrictions == null) {
                // No need to re-order as an initial order was not passed in.
                this.allowedMechanisms = foundMechanisms;
            } else {
                final Map<String, SaslServerFactory> allowedMechanisms = new LinkedHashMap<String, SaslServerFactory>();
                for (String name : restrictions) {
                    if (foundMechanisms.containsKey(name)) {
                        allowedMechanisms.put(name, foundMechanisms.get(name));
                    }
                }

                this.allowedMechanisms = allowedMechanisms;
            }
        }



        public void handleEvent(final ConnectedMessageChannel channel) {
            final Pooled<ByteBuffer> pooledBuffer = connection.allocate();
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
                                default: {
                                    server.tracef("Server received unknown capability %02x", Integer.valueOf(type & 0xff));
                                    // unknown, skip it for forward compatibility.
                                    break;
                                }
                            }
                        }
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
                            if (starttls) {
                                try {
                                    connection.getSslChannel().startHandshake();
                                } catch (IOException e) {
                                    connection.handleException(e);
                                }
                            }
                            ok = true;
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
                        final SaslServerFactory saslServerFactory = allowedMechanisms.get(mechName);
                        final AuthorizingCallbackHandler callbackHandler = serverAuthenticationProvider.getCallbackHandler(mechName);
                        if (saslServerFactory == null || callbackHandler == null) {
                            // reject
                            authLog.rejectedInvalidMechanism(mechName);
                            final Pooled<ByteBuffer> pooled = connection.allocate();
                            final ByteBuffer sendBuffer = pooled.getResource();
                            sendBuffer.put(Protocol.AUTH_REJECTED);
                            sendBuffer.flip();
                            connection.send(pooled);
                            return;
                        }
                        final SaslServer saslServer = AccessController.doPrivileged(new PrivilegedAction<SaslServer>() {
                            public SaslServer run() {
                                try {
                                    return saslServerFactory.createSaslServer(mechName, "remote", serverName, propertyMap, callbackHandler);
                                } catch (SaslException e) {
                                    connection.handleException(e);
                                    return null;
                                }
                            }
                        }, accessControlContext);
                        if (saslServer == null) {
                            // bail out
                            return;
                        }
                        connection.getChannel().suspendReads();
                        connection.getExecutor().execute(new AuthStepRunnable(true, saslServer, callbackHandler, receiveBuffer, remoteEndpointName));
                        return;
                    }
                    default: {
                        server.unknownProtocolId(msgType);
                        connection.handleException(RemoteLogger.log.invalidMessage(connection));
                        break;
                    }
                }
            } catch (BufferUnderflowException e) {
                connection.handleException(RemoteLogger.log.invalidMessage(connection));
                return;
            } catch (BufferOverflowException e) {
                connection.handleException(RemoteLogger.log.invalidMessage(connection));
                return;
            } finally {
                pooledBuffer.free();
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
                for (String mechName : allowedMechanisms.keySet()) {
                    ProtocolUtils.writeString(sendBuffer, Protocol.CAP_SASL_MECH, mechName);
                }
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
        private final AuthorizingCallbackHandler authorizingCallbackHandler;
        private final ByteBuffer buffer;
        private final String remoteEndpointName;


        AuthStepRunnable(final boolean isInitial, final SaslServer saslServer, final AuthorizingCallbackHandler authorizingCallbackHandler, final ByteBuffer buffer, final String remoteEndpointName) {
            this.isInitial = isInitial;
            this.saslServer = saslServer;
            this.authorizingCallbackHandler = authorizingCallbackHandler;
            this.buffer = buffer;
            this.remoteEndpointName = remoteEndpointName;
        }

        @Override
        public void run() {
            boolean ok = false;
            boolean close = false;
            final Pooled<ByteBuffer> pooled = connection.allocate();
            try {
                final ByteBuffer sendBuffer = pooled.getResource();
                int p = sendBuffer.position();
                try {
                    sendBuffer.put(Protocol.AUTH_COMPLETE);
                    if (SaslUtils.evaluateResponse(saslServer, sendBuffer, buffer)) {
                        server.tracef("Server sending authentication complete");
                        final Collection<Principal> principals = createPrincipals();
                        final UserInfo userInfo = authorizingCallbackHandler.createUserInfo(principals);
                        connectionProviderContext.accept(new ConnectionHandlerFactory() {
                            public ConnectionHandler createInstance(final ConnectionHandlerContext connectionContext) {
                                final Object qop = saslServer.getNegotiatedProperty(Sasl.QOP);
                                if (!isInitial && ("auth-int".equals(qop) || "auth-conf".equals(qop))) {
                                    connection.setSaslWrapper(SaslWrapper.create(saslServer));
                                }
                                final RemoteConnectionHandler connectionHandler = new RemoteConnectionHandler(
                                        connectionContext, connection, principals, userInfo, remoteEndpointName);
                                connection.setReadListener(new RemoteReadListener(connectionHandler, connection), false);
                                return connectionHandler;
                            }
                        });
                    } else {
                        server.tracef("Server sending authentication challenge");
                        sendBuffer.put(p, Protocol.AUTH_CHALLENGE);
                        if (isInitial) {
                            connection.setReadListener(new Authentication(saslServer, authorizingCallbackHandler, remoteEndpointName), false);
                        }
                    }
                } catch (Throwable e) {
                    server.tracef("Server sending authentication rejected (%s)", e);                    
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
                connection.getChannel().resumeReads();
                ok = true;
                return;
            } finally {
                if (!ok) {
                    pooled.free();
                }
            }
        }


        private Collection<Principal> createPrincipals() {
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
            String authorizationId = saslServer.getAuthorizationID();
            if (authorizationId != null) {
                principals.add(new UserPrincipal(authorizationId));
            }
            final ConnectedMessageChannel channel = connection.getChannel();
            final InetSocketAddress address = channel.getPeerAddress(InetSocketAddress.class);
            if (address != null) {
                principals.add(new InetAddressPrincipal(address.getAddress()));
            }

            return Collections.unmodifiableCollection(principals);
        }

    }

    final class Authentication implements ChannelListener<ConnectedMessageChannel> {

        private final SaslServer saslServer;
        private final AuthorizingCallbackHandler authorizingCallbackHandler;
        private final String remoteEndpointName;

        Authentication(final SaslServer saslServer, final AuthorizingCallbackHandler authorizingCallbackHandler, final String remoteEndpointName) {
            this.saslServer = saslServer;
            this.authorizingCallbackHandler = authorizingCallbackHandler;
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
                        connection.getExecutor().execute(new AuthStepRunnable(false, saslServer, authorizingCallbackHandler, buffer, remoteEndpointName));
                        return;
                    }
                    case Protocol.CAPABILITIES: {
                        server.trace("Server received capabilities request (cancelling authentication)");
                        saslDispose(saslServer);
                        final Initial initial = new Initial();
                        connection.setReadListener(initial, true);
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
            } catch (BufferUnderflowException e) {
                connection.handleException(RemoteLogger.log.invalidMessage(connection));
                saslDispose(saslServer);
                return;
            } catch (BufferOverflowException e) {
                connection.handleException(RemoteLogger.log.invalidMessage(connection));
                saslDispose(saslServer);
                return;
            } finally {
                pooledBuffer.free();
            }
        }
    }
}
