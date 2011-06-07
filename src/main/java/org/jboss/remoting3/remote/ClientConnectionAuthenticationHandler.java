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
import java.nio.ByteBuffer;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.Pooled;
import org.xnio.channels.ConnectedMessageChannel;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

final class ClientConnectionAuthenticationHandler implements ChannelListener<ConnectedMessageChannel> {

    private final RemoteConnection connection;
    private final SaslClient saslClient;

    ClientConnectionAuthenticationHandler(final RemoteConnection connection, final SaslClient saslClient) {
        this.connection = connection;
        this.saslClient = saslClient;
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
                case Protocol.AUTH_CHALLENGE: {
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
                    } catch (SaslException e) {
                        connection.handleException(e);
                        return;
                    }
                    final Pooled<ByteBuffer> pooled = connection.allocate();
                    final ByteBuffer sendBuffer = pooled.getResource();
                    sendBuffer.put(Protocol.AUTH_RESPONSE);
                    sendBuffer.put(response);
                    sendBuffer.flip();
                    connection.send(pooled);
                    return;
                }
                case Protocol.AUTH_COMPLETE: {
                    final boolean clientComplete = saslClient.isComplete();
                    final byte[] challenge = Buffers.take(buffer, buffer.remaining());
                    if (! clientComplete) try {
                        final byte[] response = saslClient.evaluateChallenge(challenge);
                        if (response != null && response.length > 0) {
                            connection.handleException(new SaslException("Received extra auth message after completion"));
                            return;
                        }
                        if (! saslClient.isComplete()) {
                            connection.handleException(new SaslException("Client not complete after processing auth complete message"));
                            return;
                        }
                    } catch (SaslException e) {
                        connection.handleException(e);
                        return;
                    }
                    // auth complete.
                    final ConnectionHandlerFactory connectionHandlerFactory = new ConnectionHandlerFactory() {
                        public ConnectionHandler createInstance(final ConnectionHandlerContext connectionContext) {
                            // this happens immediately.
                            final RemoteConnectionHandler connectionHandler = new RemoteConnectionHandler(connectionContext, connection);
                            connection.setReadListener(new RemoteReadListener(connectionHandler, connection));
                            return connectionHandler;
                        }
                    };
                    connection.getResult().setResult(connectionHandlerFactory);
                    return;
                }
                case Protocol.AUTH_REJECTED: {
                    connection.handleException(new SaslException("Authentication failed"));
                    return;
                }
            }
        } finally {
            pooledBuffer.free();
        }
    }
}
