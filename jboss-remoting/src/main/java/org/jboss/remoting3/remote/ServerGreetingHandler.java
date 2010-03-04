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

import java.nio.ByteBuffer;
import java.util.Map;
import org.jboss.remoting3.security.ServerAuthenticationProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.IoUtils;

import javax.security.sasl.SaslServerFactory;

final class ServerGreetingHandler extends AbstractMessageHandler {
    private final RemoteConnection connection;
    private final ConnectionProviderContext connectionProviderContext;
    private final Map<String, SaslServerFactory> saslMechs;
    private final ServerAuthenticationProvider provider;
    private final Map<String, Object> propertyMap;

    ServerGreetingHandler(final RemoteConnection connection, final ConnectionProviderContext connectionProviderContext, final Map<String, SaslServerFactory> saslMechs, final ServerAuthenticationProvider provider, final Map<String, Object> propertyMap) {
        super(connection);
        this.connection = connection;
        this.connectionProviderContext = connectionProviderContext;
        this.saslMechs = saslMechs;
        this.provider = provider;
        this.propertyMap = propertyMap;
    }

    public void handleMessage(final ByteBuffer buffer) {
        switch (buffer.get()) {
            case RemoteProtocol.GREETING: {
                RemoteConnectionHandler.log.trace("Server received greeting message");
                final int[] ourVersions = connection.getProviderDescriptor().getSupportedVersions();
                int bestVersion = -1;
                while (buffer.hasRemaining()) {
                    final byte type = buffer.get();
                    final int len = buffer.get() & 0xff;
                    final ByteBuffer data = Buffers.slice(buffer, len);
                    switch (type) {
                        case RemoteProtocol.GREETING_VERSION: {
                            // We only support version zero, so knowing the other side's version is not useful presently
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
                connection.setMessageHandler(new ServerInitialAuthenticationHandler(connection, propertyMap, saslMechs, provider, connectionProviderContext));
                return;
            }
            default: {
                RemoteConnectionHandler.log.warn("Server received invalid greeting message");
                IoUtils.safeClose(connection);
            }
        }
    }
}
