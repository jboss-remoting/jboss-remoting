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
import java.nio.ByteBuffer;
import java.util.Map;
import org.jboss.remoting3.security.ServerAuthenticationProvider;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Pooled;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.sasl.SaslUtils;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

import static org.jboss.remoting3.remote.RemoteAuthLogger.authLog;
import static org.jboss.remoting3.remote.RemoteLogger.log;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ServerConnectionInitialAuthenticationHandler implements ChannelListener<ConnectedMessageChannel> {

    private final RemoteConnection connection;
    private final Map<String, SaslServerFactory> allowedMechanisms;
    private final ServerAuthenticationProvider serverAuthenticationProvider;
    private final String remoteEndpointName;
    private final OptionMap optionMap;
    private final ConnectionProviderContext connectionProviderContext;
    private final Map<String,?> propertyMap;

    ServerConnectionInitialAuthenticationHandler(final RemoteConnection connection, final Map<String, SaslServerFactory> allowedMechanisms, final ServerAuthenticationProvider serverAuthenticationProvider, final String remoteEndpointName, final OptionMap optionMap, final ConnectionProviderContext connectionProviderContext, final Map<String, ?> propertyMap) {
        this.connection = connection;
        this.allowedMechanisms = allowedMechanisms;
        this.serverAuthenticationProvider = serverAuthenticationProvider;
        this.remoteEndpointName = remoteEndpointName;
        this.optionMap = optionMap;
        this.connectionProviderContext = connectionProviderContext;
        this.propertyMap = propertyMap;
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
            buffer.flip();
            final byte msgType = buffer.get();
            switch (msgType) {
                case Protocol.AUTH_REQUEST: {
                    final String mechName = Buffers.getModifiedUtf8(buffer);
                    final SaslServerFactory saslServerFactory = allowedMechanisms.get(mechName);
                    final CallbackHandler callbackHandler = serverAuthenticationProvider.getCallbackHandler(mechName);
                    if (saslServerFactory == null || callbackHandler == null) {
                        // reject
                        authLog.rejectedInvalidMechanism(mechName);
                        final Pooled<ByteBuffer> pooled = connection.allocate();
                        final ByteBuffer sendBuffer = pooled.getResource();
                        sendBuffer.put(Protocol.AUTH_REJECTED);
                        Buffers.putModifiedUtf8(sendBuffer, "Unsupported mechanism");
                        sendBuffer.flip();
                        connection.send(pooled);
                        return;
                    }
                    final SaslServer saslServer;
                    try {
                        saslServer = saslServerFactory.createSaslServer(mechName, "remote", connectionProviderContext.getEndpoint().getName(), propertyMap, callbackHandler);
                    } catch (SaslException e) {
                        connection.handleException(e);
                        return;
                    }
                    final boolean clientComplete = saslServer.isComplete();
                    if (clientComplete) {
                        connection.handleException(new SaslException("Received extra auth message after completion"));
                        return;
                    }
                    boolean ok = false;
                    final Pooled<ByteBuffer> pooled = connection.allocate();
                    try {
                        final ByteBuffer sendBuffer = pooled.getResource();
                        try {
                            int p = sendBuffer.position();
                            sendBuffer.put(Protocol.AUTH_COMPLETE);
                            if (SaslUtils.evaluateResponse(saslServer, sendBuffer, buffer)) {
                                connectionProviderContext.accept(new ConnectionHandlerFactory() {
                                    public ConnectionHandler createInstance(final ConnectionHandlerContext connectionContext) {
                                        final RemoteConnectionHandler connectionHandler = new RemoteConnectionHandler(connectionContext, connection);
                                        connection.setReadListener(new RemoteReadListener(connectionHandler, connection));
                                        return connectionHandler;
                                    }
                                });
                            } else {
                                sendBuffer.put(p, Protocol.AUTH_CHALLENGE);
                            }
                        } catch (SaslException e) {
                            connection.handleException(e);
                            return;
                        }
                        sendBuffer.flip();
                        connection.send(pooled);
                        ok = true;
                        return;
                    } finally {
                        if (! ok) {
                            pooled.free();
                        }
                    }
                }
                default: {
                    log.unknownProtocolId(msgType);
                    break;
                }
            }
        } finally {
            pooledBuffer.free();
        }
    }
}
