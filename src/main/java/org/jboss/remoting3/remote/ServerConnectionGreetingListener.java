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
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Pooled;
import org.xnio.channels.ConnectedMessageChannel;

import javax.security.sasl.SaslServerFactory;

import static org.jboss.remoting3.remote.RemoteLogger.log;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ServerConnectionGreetingListener implements ChannelListener<ConnectedMessageChannel> {

    private final RemoteConnection connection;
    private final Map<String, SaslServerFactory> allowedMechanisms;
    private final ServerAuthenticationProvider serverAuthenticationProvider;
    private final OptionMap optionMap;
    private final ConnectionProviderContext connectionProviderContext;
    private final Map<String,?> propertyMap;

    ServerConnectionGreetingListener(final RemoteConnection connection, final Map<String, SaslServerFactory> allowedMechanisms, final ServerAuthenticationProvider serverAuthenticationProvider, final OptionMap optionMap, final ConnectionProviderContext connectionProviderContext, final Map<String, ?> propertyMap) {
        this.connection = connection;
        this.allowedMechanisms = allowedMechanisms;
        this.serverAuthenticationProvider = serverAuthenticationProvider;
        this.optionMap = optionMap;
        this.connectionProviderContext = connectionProviderContext;
        this.propertyMap = propertyMap;
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
                connection.handleException(log.abruptClose(connection));
                return;
            }
            if (res == 0) {
                return;
            }
            String remoteEndpointName = "endpoint";
            final byte msgType = receiveBuffer.get();
            switch (msgType) {
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
                            case Protocol.GREETING_ENDPOINT_NAME: {
                                remoteEndpointName = ProtocolUtils.readString(data);
                                break;
                            }
                            default: {
                                // unknown, skip it for forward compatibility.
                                break;
                            }
                        }
                    }
                    connection.setReadListener(new ServerConnectionInitialAuthenticationHandler(connection, allowedMechanisms, serverAuthenticationProvider, remoteEndpointName, optionMap, connectionProviderContext, propertyMap));
                    return;
                }
                default: {
                    log.unknownProtocolId(msgType);
                    break;
                }
            }
        } finally {
            pooledReceiveBuffer.free();
        }
    }
}
