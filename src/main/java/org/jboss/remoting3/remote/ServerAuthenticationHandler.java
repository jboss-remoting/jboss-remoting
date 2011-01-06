/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.SpiUtils;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

final class ServerAuthenticationHandler extends AbstractMessageHandler {
    private final RemoteConnection remoteConnection;
    private final SaslServer saslServer;
    private final ConnectionProviderContext connectionProviderContext;
    private final ServerInitialAuthenticationHandler initialAuthHandler;

    private static final Logger log = Loggers.serverSasl;

    ServerAuthenticationHandler(final RemoteConnection remoteConnection, final SaslServer saslServer, final ConnectionProviderContext connectionProviderContext, final ServerInitialAuthenticationHandler initialAuthHandler) {
        super(remoteConnection);
        this.saslServer = saslServer;
        this.remoteConnection = remoteConnection;
        this.connectionProviderContext = connectionProviderContext;
        this.initialAuthHandler = initialAuthHandler;
    }

    public void handleMessage(final ByteBuffer buffer) {
        switch (buffer.get()) {
            case RemoteProtocol.AUTH_RESPONSE: {
                log.trace("Received SASL response");
                final byte[] challenge;
                try {
                    try {
                        challenge = saslServer.evaluateResponse(Buffers.take(buffer, buffer.remaining()));
                    } catch (SaslException e) {
                        log.trace(e, "Server authentication failed");
                        initialAuthHandler.rejectAuth();
                        remoteConnection.setMessageHandler(initialAuthHandler);
                        return;
                    }
                    final boolean complete = saslServer.isComplete();
                    if (complete) {
                        log.trace("Sending SASL complete");
                        remoteConnection.sendAuthMessage(RemoteProtocol.AUTH_COMPLETE, challenge);
                        connectionProviderContext.accept(new ConnectionHandlerFactory() {
                            public ConnectionHandler createInstance(final ConnectionHandlerContext connectionContext) {
                                final MarshallerFactory marshallerFactory = remoteConnection.getProviderDescriptor().getMarshallerFactory();
                                final RemoteConnectionHandler connectionHandler = new RemoteConnectionHandler(connectionContext, remoteConnection, marshallerFactory);
                                remoteConnection.addCloseHandler(SpiUtils.closingCloseHandler(connectionHandler));
                                remoteConnection.setMessageHandler(new RemoteMessageHandler(connectionHandler, remoteConnection));
                                return connectionHandler;
                            }
                        });
                    } else {
                        log.trace("Sending subsequent SASL challenge");
                        remoteConnection.sendAuthMessage(RemoteProtocol.AUTH_CHALLENGE, challenge);
                    }
                } catch (IOException e) {
                    log.trace(e, "Failed to send auth message");
                    IoUtils.safeClose(remoteConnection);
                }
                break;
            }
            default: {
                log.warn("Server received invalid message on %s", remoteConnection);
                IoUtils.safeClose(remoteConnection);
            }
        }
    }
}
