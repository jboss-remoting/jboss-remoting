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
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jboss.remoting3.RemotingOptions;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Pooled;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.sasl.SaslUtils;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ClientConnectionGreetingListener implements ChannelListener<ConnectedMessageChannel> {

    private final RemoteConnection connection;
    private final CallbackHandler callbackHandler;
    private final AccessControlContext accessControlContext;

    ClientConnectionGreetingListener(final RemoteConnection connection, final CallbackHandler handler, final AccessControlContext context) {
        this.connection = connection;
        callbackHandler = handler;
        accessControlContext = context;
    }

    public void handleEvent(final ConnectedMessageChannel channel) {
        final Pooled<ByteBuffer> pooledReceiveBuffer = connection.allocate();
        try {
            final ByteBuffer receiveBuffer = pooledReceiveBuffer.getResource();
            int res = 0;
            try {
                res = channel.receive(receiveBuffer);
            } catch (IOException e) {
                connection.handleException(e);
                return;
            }
            if (res == -1) {
                connection.handleException(RemoteLogger.log.abruptClose(connection));
                return;
            }
            if (res == 0) {
                return;
            }
            final Set<String> saslMechs = new LinkedHashSet<String>();
            String remoteEndpointName = "endpoint";
            switch (receiveBuffer.get()) {
                case Protocol.GREETING: {
                    while (receiveBuffer.hasRemaining()) {
                        final byte type = receiveBuffer.get();
                        final int len = receiveBuffer.get() & 0xff;
                        final ByteBuffer data = Buffers.slice(receiveBuffer, len);
                        switch (type) {
                            case Protocol.GREETING_VERSION: {
                                // We only support version zero, so knowing the other side's version is not useful presently
                                break;
                            }
                            case Protocol.GREETING_SASL_MECH: {
                                saslMechs.add(Buffers.getModifiedUtf8(data));
                                break;
                            }
                            case Protocol.GREETING_ENDPOINT_NAME: {
                                remoteEndpointName = Buffers.getModifiedUtf8(data);
                                break;
                            }
                            default: {
                                // unknown, skip it for forward compatibility.
                                break;
                            }
                        }
                    }
                    if (saslMechs.isEmpty()) {
                        connection.handleException(new SaslException("No more authentication mechanisms to try"));
                        return;
                    }
                    // OK now send our authentication request
                    final OptionMap optionMap = connection.getOptionMap();
                    final String userName = optionMap.get(RemotingOptions.AUTHORIZE_ID);
                    final Map<String, ?> propertyMap = SaslUtils.createPropertyMap(optionMap);
                    final SaslClient saslClient;
                    try {
                        final String finalRemoteEndpointName = remoteEndpointName;
                        saslClient = AccessController.doPrivileged(new PrivilegedExceptionAction<SaslClient>() {
                            public SaslClient run() throws SaslException {
                                return Sasl.createSaslClient(saslMechs.toArray(new String[saslMechs.size()]), userName, "remote", finalRemoteEndpointName, propertyMap, callbackHandler);
                            }
                        }, accessControlContext);
                    } catch (PrivilegedActionException e) {
                        final SaslException se = (SaslException) e.getCause();
                        connection.handleException(se);
                        return;
                    }
                    final String mechanismName = saslClient.getMechanismName();
                    // Prepare the request message body
                    final Pooled<ByteBuffer> pooledSendBuffer = connection.allocate();
                    final ByteBuffer sendBuffer = pooledSendBuffer.getResource();
                    sendBuffer.putInt(0);
                    sendBuffer.put(Protocol.AUTH_REQUEST);
                    Buffers.putModifiedUtf8(sendBuffer, mechanismName);
                    sendBuffer.flip();
                    connection.send(pooledSendBuffer);
                    connection.setReadListener(new ClientConnectionAuthenticationHandler(connection, saslClient));
                    return;
                }
                default: {
                    connection.handleException(RemoteLogger.log.invalidMessage(connection));
                    return;
                }
            }
        } catch (BufferUnderflowException e) {
            connection.handleException(RemoteLogger.log.invalidMessage(connection));
            return;
        } catch (BufferOverflowException e) {
            connection.handleException(RemoteLogger.log.invalidMessage(connection));
            return;
        } finally {
            pooledReceiveBuffer.free();
        }
    }
}
