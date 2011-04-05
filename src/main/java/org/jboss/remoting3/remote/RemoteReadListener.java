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
import java.nio.ByteBuffer;
import java.util.Random;
import org.xnio.ChannelListener;
import org.xnio.Pooled;
import org.xnio.channels.ConnectedMessageChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteReadListener implements ChannelListener<ConnectedMessageChannel> {

    private final RemoteConnectionHandler handler;
    private final RemoteConnection connection;

    RemoteReadListener(final RemoteConnectionHandler handler, final RemoteConnection connection) {
        this.handler = handler;
        this.connection = connection;
    }

    public void handleEvent(final ConnectedMessageChannel channel) {
        final Pooled<ByteBuffer> pooled = connection.allocate();
        final ByteBuffer buffer = pooled.getResource();
        int res;
        try {
            while ((res = channel.receive(buffer)) > 0) {
                final int protoId = buffer.get() & 0xff;
                switch (protoId) {
                    case Protocol.CONNECTION_ALIVE: {
                        break;
                    }
                    case Protocol.CHANNEL_OPEN_REQUEST: {
                        int channelId = buffer.getInt();
                        int inboundWindow = 0x10000;
                        int outboundWindow = 0x10000;
                        // parse out request

                        // construct the channel
                        RemoteConnectionChannel connectionChannel = new RemoteConnectionChannel(handler.getConnectionContext().getConnectionProviderContext().getExecutor(), connection, channelId, new Random(), outboundWindow, inboundWindow);
                        handler.addChannel(connectionChannel);
                        break;
                    }
                    case Protocol.MESSAGE_DATA: {
                        int channelId = buffer.getInt();
                        RemoteConnectionChannel connectionChannel = handler.getChannel(channelId);
                        if (connectionChannel == null) {
                            connection.handleException(new IOException("Message data for non-existent channel"));
                            break;
                        }
                        connectionChannel.handleMessageData(pooled);
                        break;
                    }
                    case Protocol.MESSAGE_WINDOW_OPEN: {
                        int channelId = buffer.getInt() ^ 0x80000000;
                        RemoteConnectionChannel connectionChannel = handler.getChannel(channelId);
                        if (connectionChannel == null) {
                            connection.handleException(new IOException("Window open for non-existent channel"));
                            break;
                        }
                        connectionChannel.handleWindowOpen(pooled);
                        break;
                    }
                }
            }
            if (res == -1) {
                try {
                    channel.shutdownReads();
                } catch (IOException e) {
                    RemoteLogger.log.debugf("Failed to shut down reads on %s: %s", connection, e);
                }
            }
        } catch (IOException e) {
            connection.handleException(e);
        }
    }
}
