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

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslServer;

final class ServerInitialAuthenticationHandler extends AbstractMessageHandler {
    private final RemoteConnection remoteConnection;
    private final Map<String, ?> saslPropertyMap;
    private final Set<String> allowedMechs;
    private final ServerAuthenticationProvider authenticationProvider;
    private final ConnectionProviderContext connectionProviderContext;

    ServerInitialAuthenticationHandler(final RemoteConnection remoteConnection, final Map<String, ?> saslPropertyMap, final Set<String> allowedMechs, final ServerAuthenticationProvider authenticationProvider, final ConnectionProviderContext connectionProviderContext) {
        super(remoteConnection);
        this.remoteConnection = remoteConnection;
        this.saslPropertyMap = saslPropertyMap;
        this.allowedMechs = allowedMechs;
        this.authenticationProvider = authenticationProvider;
        this.connectionProviderContext = connectionProviderContext;
    }

    public void handleMessage(final ByteBuffer buffer) {
        switch (buffer.get()) {
            case RemoteProtocol.AUTH_REQUEST: {
                try {
                    // mech name
                    final String name = Buffers.getModifiedUtf8(buffer);
                    if (allowedMechs.contains(name)) {
                        RemoteConnectionHandler.log.trace("Selected SASL mechanism %s", name);
                        final String realm = connectionProviderContext.getEndpoint().getName();
                        final SaslServer server = Sasl.createSaslServer(name, "remote", realm, saslPropertyMap, authenticationProvider.getCallbackHandler());
                        remoteConnection.setMessageHandler(new ServerAuthenticationHandler(remoteConnection, server, connectionProviderContext));
                        RemoteConnectionHandler.log.trace("Sending initial challenge");
                        remoteConnection.sendAuthMessage(RemoteProtocol.AUTH_CHALLENGE, server.evaluateResponse(SaslUtils.EMPTY));
                        return;
                    } else {
                        RemoteConnectionHandler.log.trace("Rejected invalid SASL mechanism %s", name);
                        remoteConnection.sendAuthReject("Invalid mechanism name");
                        return;
                    }
                } catch (IOException e) {
                    IoUtils.safeClose(remoteConnection);
                    RemoteConnectionHandler.log.trace("Failed to send auth message: %s", e);
                    return;
                }
            }
            default: {
                RemoteConnectionHandler.log.warn("Server received invalid auth request message");
                IoUtils.safeClose(remoteConnection);
            }
        }
    }
}
