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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import org.jboss.marshalling.ProviderDescriptor;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Options;
import org.jboss.xnio.Result;
import org.jboss.xnio.channels.ConnectedStreamChannel;

import javax.security.auth.callback.CallbackHandler;

final class ClientOpenListener implements ChannelListener<ConnectedStreamChannel<InetSocketAddress>> {

    private final OptionMap optionMap;
    private final ConnectionProviderContext connectionProviderContext;
    private final Result<ConnectionHandlerFactory> factoryResult;
    private final CallbackHandler callbackHandler;
    private final ProviderDescriptor providerDescriptor;

    ClientOpenListener(final OptionMap optionMap, final ConnectionProviderContext connectionProviderContext, final Result<ConnectionHandlerFactory> factoryResult, final CallbackHandler callbackHandler, final ProviderDescriptor providerDescriptor) {
        this.optionMap = optionMap;
        this.connectionProviderContext = connectionProviderContext;
        this.factoryResult = factoryResult;
        this.callbackHandler = callbackHandler;
        this.providerDescriptor = providerDescriptor;
    }

    public void handleEvent(final ConnectedStreamChannel<InetSocketAddress> channel) {
        try {
            channel.setOption(Options.TCP_NODELAY, Boolean.TRUE);
        } catch (IOException e) {
            // ignore
        }
        final RemoteConnection connection = new RemoteConnection(connectionProviderContext.getExecutor(), channel, optionMap, providerDescriptor);
        // Send client greeting packet...
        final ByteBuffer buffer = connection.allocate();
        // length placeholder
        buffer.putInt(0);
        buffer.put(RemoteProtocol.GREETING);
        // marshaller versions
        final int[] versions = providerDescriptor.getSupportedVersions();
        for (int version : versions) {
            GreetingUtils.writeInt(buffer, RemoteProtocol.GREETING_MARSHALLER_VERSION, version);
        }
        // version ID
        GreetingUtils.writeByte(buffer, RemoteProtocol.GREETING_VERSION, RemoteProtocol.VERSION);
        // that's it!
        buffer.flip();
        buffer.putInt(0, buffer.remaining() - 4);
        channel.getWriteSetter().set(new ChannelListener<ConnectedStreamChannel<InetSocketAddress>>() {
            public void handleEvent(final ConnectedStreamChannel<InetSocketAddress> channel) {
                for (;;) {
                    while (buffer.hasRemaining()) {
                        final int res;
                        try {
                            res = channel.write(buffer);
                        } catch (IOException e1) {
                            RemoteConnectionHandler.log.trace(e1, "Failed to send client greeting message");
                            factoryResult.setException(e1);
                            IoUtils.safeClose(connection);
                            connection.free(buffer);
                            return;
                        }
                        if (res == 0) {
                            channel.resumeWrites();
                            return;
                        }
                    }
                    connection.free(buffer);
                    try {
                        while (! channel.flush());
                    } catch (IOException e) {
                        RemoteConnectionHandler.log.trace(e, "Failed to flush client greeting message");
                        factoryResult.setException(e);
                        IoUtils.safeClose(connection);
                        return;
                    }
                    RemoteConnectionHandler.log.trace("Client sent greeting message");
                    channel.resumeReads();
                    return;
                }
            }
        });

        connection.setMessageHandler(new ClientGreetingHandler(connection, factoryResult, callbackHandler));
        // and send the greeting
        channel.resumeWrites();
    }
}