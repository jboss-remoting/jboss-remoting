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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jboss.remoting3.ProtocolException;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Result;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

final class ClientGreetingHandler extends AbstractClientMessageHandler {
    private final RemoteConnection connection;
    private final Result<ConnectionHandlerFactory> factoryResult;
    private final CallbackHandler callbackHandler;

    ClientGreetingHandler(final RemoteConnection connection, final Result<ConnectionHandlerFactory> factoryResult, final CallbackHandler callbackHandler) {
        super(connection, factoryResult);
        this.connection = connection;
        this.factoryResult = factoryResult;
        this.callbackHandler = callbackHandler;
    }

    public void handleMessage(final ByteBuffer buffer) {
        Set<String> saslMechs = new LinkedHashSet<String>();
        String remoteEndpointName = "endpoint";
        final int[] ourVersions = connection.getProviderDescriptor().getSupportedVersions();
        int bestVersion = -1;
        switch (buffer.get()) {
            case RemoteProtocol.GREETING: {
                RemoteConnectionHandler.log.trace("Client received greeting message");
                while (buffer.hasRemaining()) {
                    final byte type = buffer.get();
                    final int len = buffer.get() & 0xff;
                    final ByteBuffer data = Buffers.slice(buffer, len);
                    switch (type) {
                        case RemoteProtocol.GREETING_VERSION: {
                            // We only support version zero, so knowing the other side's version is not useful presently
                            break;
                        }
                        case RemoteProtocol.GREETING_SASL_MECH: {
                            saslMechs.add(Buffers.getModifiedUtf8(data));
                            break;
                        }
                        case RemoteProtocol.GREETING_ENDPOINT_NAME: {
                            remoteEndpointName = Buffers.getModifiedUtf8(data);
                            break;
                        }
                        case RemoteProtocol.GREETING_MARSHALLER_VERSION: {
                            final int remoteVersion = data.getInt();
                            // is it better than the best one?  if not, don't bother
                            if (remoteVersion <= bestVersion) {
                                break;
                            }
                            // do we support it?  if not, skip
                            for (int ourVersion : ourVersions) {
                                if (ourVersion == remoteVersion) {
                                    bestVersion = remoteVersion;
                                    break;
                                }
                            }
                            break;
                        }
                        default: {
                            // unknown, skip it for forward compatibility.
                            break;
                        }
                    }
                }
                // Check the greeting
                if (bestVersion == -1) {
                    // no matches.
                    factoryResult.setException(new ProtocolException("No matching Marshalling versions could be found"));
                    IoUtils.safeClose(connection);
                    return;
                }
                if (saslMechs.isEmpty()) {
                    factoryResult.setException(new SaslException("No more authentication mechanisms to try"));
                    IoUtils.safeClose(connection);
                    return;
                }
                // OK now send our authentication request
                final OptionMap optionMap = connection.getOptionMap();
                final String userName = optionMap.get(RemotingOptions.AUTH_USER_NAME);
                final Map<String, ?> propertyMap = SaslUtils.createPropertyMap(optionMap);
                final SaslClient saslClient;
                try {
                    saslClient = Sasl.createSaslClient(saslMechs.toArray(new String[saslMechs.size()]), userName, "remote", remoteEndpointName, propertyMap, callbackHandler);
                } catch (SaslException e) {
                    factoryResult.setException(e);
                    RemoteConnectionHandler.log.trace(e, "Client connect authentication error");
                    try {
                        remoteConnection.shutdownWritesBlocking();
                    } catch (IOException e1) {
                        RemoteConnectionHandler.log.trace(e1, "Failed to shutdown writes on %s", remoteConnection);
                    }
                    return;
                }
                final String mechanismName = saslClient.getMechanismName();
                RemoteConnectionHandler.log.trace("Sasl mechanism selected: %s", mechanismName);
                final ByteBuffer outBuf = connection.allocate();
                try {
                    outBuf.putInt(0);
                    outBuf.put(RemoteProtocol.AUTH_REQUEST);
                    Buffers.putModifiedUtf8(outBuf, mechanismName);
                    outBuf.flip();
                    connection.sendBlocking(outBuf, true);
                } catch (IOException e) {
                    RemoteConnectionHandler.log.trace(e, "Failed to send auth request on %s", remoteConnection);
                    factoryResult.setException(e);
                    return;
                } finally {
                    connection.free(outBuf);
                }
                connection.setMessageHandler(new ClientAuthenticationHandler(connection, saslClient, factoryResult));
                return;
            }
            default: {
                RemoteConnectionHandler.log.warn("Received invalid greeting packet on %s", remoteConnection);
                try {
                    remoteConnection.shutdownWritesBlocking();
                } catch (IOException e1) {
                    RemoteConnectionHandler.log.trace(e1, "Failed to shutdown writes on %s", remoteConnection);
                }
                return;
            }
        }
    }
}