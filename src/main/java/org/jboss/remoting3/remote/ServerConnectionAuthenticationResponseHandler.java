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
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Pooled;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.sasl.SaslUtils;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import static org.jboss.remoting3.remote.RemoteLogger.log;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ServerConnectionAuthenticationResponseHandler implements ChannelListener<ConnectedMessageChannel> {

    private final RemoteConnection connection;
    private final ServerConnectionInitialAuthenticationHandler initialAuthenticationHandler;
    private final String endpointName;
    private final OptionMap optionMap;
    private final ConnectionProviderContext connectionProviderContext;
    private final SaslServer saslServer;

    ServerConnectionAuthenticationResponseHandler(final RemoteConnection connection, final ServerConnectionInitialAuthenticationHandler initialAuthenticationHandler, final String endpointName, final OptionMap optionMap, final ConnectionProviderContext connectionProviderContext, final SaslServer saslServer) {
        this.connection = connection;
        this.initialAuthenticationHandler = initialAuthenticationHandler;
        this.endpointName = endpointName;
        this.optionMap = optionMap;
        this.connectionProviderContext = connectionProviderContext;
        this.saslServer = saslServer;
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
                case Protocol.AUTH_RESPONSE: {
                    boolean ok = false;
                    boolean close = false;
                    final Pooled<ByteBuffer> pooled = connection.allocate();
                    try {
                        final ByteBuffer sendBuffer = pooled.getResource();
                        int p = sendBuffer.position();
                        try {
                            sendBuffer.put(Protocol.AUTH_COMPLETE);
                            if (SaslUtils.evaluateResponse(saslServer, sendBuffer, buffer) || /* todo temporary workaround */ saslServer.isComplete()) {
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
                            sendBuffer.put(p, Protocol.AUTH_REJECTED);
                            AtomicInteger retryCount = initialAuthenticationHandler.getRetryCount();
                            if (retryCount.decrementAndGet() <= 0) {
                                close = true;
                            } else {
                                connection.setReadListener(initialAuthenticationHandler);
                            }
                        }
                        sendBuffer.flip();
                        connection.send(pooled, close);
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
