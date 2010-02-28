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
import java.util.Map;
import java.util.Set;
import org.jboss.remoting3.security.ServerAuthenticationProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.MessageHandler;

final class ServerGreetingHandler implements MessageHandler {
    private final RemoteConnection connection;
    private final ConnectionProviderContext connectionProviderContext;
    private final Set<String> saslMechs;
    private final ServerAuthenticationProvider provider;
    private final Map<String, Object> propertyMap;

    public ServerGreetingHandler(final RemoteConnection connection, final ConnectionProviderContext connectionProviderContext, final Set<String> saslMechs, final ServerAuthenticationProvider provider, final Map<String, Object> propertyMap) {
        this.connection = connection;
        this.connectionProviderContext = connectionProviderContext;
        this.saslMechs = saslMechs;
        this.provider = provider;
        this.propertyMap = propertyMap;
    }

    public void handleMessage(final ByteBuffer buffer) {
        switch (buffer.get()) {
            case RemoteProtocol.GREETING: {
                while (buffer.hasRemaining()) {
                    final byte type = buffer.get();
                    final int len = buffer.get() & 0xff;
                    switch (type) {
                        case RemoteProtocol.GREETING_VERSION: {
                            // We only support version zero, so knowing the other side's version is not useful presently
                            buffer.get();
                            if (len > 1) Buffers.skip(buffer, len - 1);
                            break;
                        }
                        default: {
                            // unknown, skip it for forward compatibility.
                            Buffers.skip(buffer, len);
                            break;
                        }
                    }
                }
                connection.setMessageHandler(new ServerInitialAuthenticationHandler(connection, propertyMap, saslMechs, provider, connectionProviderContext));
                return;
            }
            default: {
                // todo log invalid greeting
                IoUtils.safeClose(connection);
            }
        }
    }

    public void handleEof() {
        IoUtils.safeClose(connection);
    }

    public void handleException(final IOException e) {
        // todo log it
        IoUtils.safeClose(connection);
    }
}
