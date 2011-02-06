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
import org.jboss.remoting3.spi.SpiUtils;
import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.Result;
import org.jboss.logging.Logger;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

final class ClientAuthenticationHandler extends AbstractClientMessageHandler {

    private final RemoteConnection remoteConnection;
    private final SaslClient saslClient;
    private final Result<ConnectionHandlerFactory> factoryResult;
    private static final Logger log = Loggers.clientSasl;

    ClientAuthenticationHandler(final RemoteConnection remoteConnection, final SaslClient saslClient, final Result<ConnectionHandlerFactory> factoryResult) {
        super(remoteConnection, factoryResult);
        this.remoteConnection = remoteConnection;
        this.saslClient = saslClient;
        this.factoryResult = factoryResult;
    }

    public void handleMessage(final ByteBuffer buffer) {
        final byte msgType = buffer.get();
        switch (msgType) {
            case RemoteProtocol.AUTH_CHALLENGE: {
                log.trace("Received challenge message");
                final boolean clientComplete = saslClient.isComplete();
                if (clientComplete) {
                    log.trace("Received extra auth challenge message on %s after completion", remoteConnection);
                    factoryResult.setException(new SaslException("Received extra auth message after completion"));
                    IoUtils.safeClose(remoteConnection);
                    return;
                }
                final byte[] response;
                final byte[] challenge = Buffers.take(buffer, buffer.remaining());
                try {
                    response = saslClient.evaluateChallenge(challenge);
                    if (msgType == RemoteProtocol.AUTH_COMPLETE && response != null && response.length > 0) {
                        log.trace("Received extra auth message on %s", remoteConnection);
                        factoryResult.setException(new SaslException("Received extra auth message after completion"));
                        IoUtils.safeClose(remoteConnection);
                        return;
                    }
                } catch (SaslException e) {
                    log.trace(e, "Authentication error");
                    factoryResult.setException(e);
                    try {
                        remoteConnection.shutdownWritesBlocking();
                    } catch (IOException e1) {
                        log.trace(e, "Unable to shut down writes");
                    }
                    return;
                }
                try {
                    log.trace("Sending SASL response");
                    remoteConnection.sendAuthMessage(RemoteProtocol.AUTH_RESPONSE, response);
                } catch (IOException e) {
                    factoryResult.setException(e);
                    log.trace("Failed to send auth response message on %s", remoteConnection);
                    IoUtils.safeClose(remoteConnection);
                    return;
                }
                return;
            }
            case RemoteProtocol.AUTH_COMPLETE: {
                log.trace("Received auth complete message");
                final boolean clientComplete = saslClient.isComplete();
                final byte[] challenge = Buffers.take(buffer, buffer.remaining());
                if (! clientComplete) try {
                    final byte[] response = saslClient.evaluateChallenge(challenge);
                    if (response != null && response.length > 0) {
                        log.trace("Received extra auth message on %s", remoteConnection);
                        factoryResult.setException(new SaslException("Received extra auth message after completion"));
                        IoUtils.safeClose(remoteConnection);
                        return;
                    }
                    if (! saslClient.isComplete()) {
                        log.trace("Client not complete after processing auth complete message on %s", remoteConnection);
                        factoryResult.setException(new SaslException("Client not complete after processing auth complete message"));
                        IoUtils.safeClose(remoteConnection);
                        return;
                    }
                } catch (SaslException e) {
                    log.trace(e, "Authentication error");
                    factoryResult.setException(e);
                    try {
                        remoteConnection.shutdownWritesBlocking();
                    } catch (IOException e1) {
                        log.trace(e, "Unable to shut down writes");
                    }
                    return;
                }
                // auth complete.
                factoryResult.setResult(new ConnectionHandlerFactory() {
                    public ConnectionHandler createInstance(final ConnectionHandlerContext connectionContext) {
                        // this happens immediately.
                        final MarshallerFactory marshallerFactory = remoteConnection.getProviderDescriptor().getMarshallerFactory();
                        final RemoteConnectionHandler connectionHandler = new RemoteConnectionHandler(connectionContext, remoteConnection, marshallerFactory);
                        remoteConnection.addCloseHandler(SpiUtils.closingCloseHandler(connectionHandler));
                        remoteConnection.setMessageHandler(new RemoteMessageHandler(connectionHandler, remoteConnection));
                        return connectionHandler;
                    }
                });
                return;
            }
            case RemoteProtocol.AUTH_REJECTED: {
                log.trace("Received auth rejected message");
                factoryResult.setException(new SaslException("Authentication failed"));
                IoUtils.safeClose(remoteConnection);
            }
        }
    }
}
