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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Random;

import org.jboss.remoting3.RemotingOptions;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedMessageChannel;

import static org.jboss.remoting3.remote.RemoteLogger.log;

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
        int res;
        try {
            Pooled<ByteBuffer> pooled = connection.allocate();
            ByteBuffer buffer = pooled.getResource();
            try {
                for (;;) try {
                    res = channel.receive(buffer);
                    if (res == -1) {
                        try {
                            channel.shutdownReads();
                            return;
                        } catch (IOException e) {
                            log.debugf("Failed to shut down reads on %s: %s", connection, e);
                        }
                    } else if (res == 0) {
                        return;
                    }
                    buffer.flip();
                    final int protoId = buffer.get() & 0xff;
                    try {
                        switch (protoId) {
                            case Protocol.CONNECTION_ALIVE: {
                                break;
                            }
                            case Protocol.CHANNEL_OPEN_REQUEST: {
                                int channelId = buffer.getInt() ^ 0x80000000;
                                int inboundWindow = Protocol.DEFAULT_WINDOW_SIZE;
                                int outboundWindow = Protocol.DEFAULT_WINDOW_SIZE;
                                int inboundMessages = Protocol.DEFAULT_MESSAGE_COUNT;
                                int outboundMessages = Protocol.DEFAULT_MESSAGE_COUNT;
                                // parse out request
                                int b;
                                String serviceType = null;
                                OUT: for (;;) {
                                    b = buffer.get() & 0xff;
                                    switch (b) {
                                        case 0: break OUT;
                                        case 0x01: {
                                            serviceType = ProtocolUtils.readString(buffer);
                                            break;
                                        }
                                        case 0x80: {
                                            outboundWindow = ProtocolUtils.readInt(buffer);
                                            break;
                                        }
                                        case 0x81: {
                                            outboundMessages = ProtocolUtils.readUnsignedShort(buffer);
                                            break;
                                        }
                                        default: {
                                            Buffers.skip(buffer, buffer.get() & 0xff);
                                            break;
                                        }
                                    }
                                }
                                if (serviceType == null) {
                                    // todo: exception
                                }

                                // construct the channel
                                RemoteConnectionChannel connectionChannel = new RemoteConnectionChannel(handler.getConnectionContext().getConnectionProviderContext().getExecutor(), connection, channelId, new Random(), outboundWindow, inboundWindow, outboundMessages, inboundMessages);
                                handler.addChannel(connectionChannel);
                                
                                //Open any services
                                handler.getConnectionContext().openService(connectionChannel, serviceType);
                                
                                // construct reply
                                Pooled<ByteBuffer> pooledReply = connection.allocate();
                                try {
                                    ByteBuffer replyBuffer = pooledReply.getResource();
                                    replyBuffer.clear();
                                    replyBuffer.put(Protocol.CHANNEL_OPEN_ACK);
                                    replyBuffer.putInt(channelId);
                                    ProtocolUtils.writeInt(replyBuffer, 0x80, outboundWindow);
                                    ProtocolUtils.writeShort(replyBuffer, 0x81, outboundMessages);
                                    replyBuffer.put((byte) 0);
                                    replyBuffer.flip();
                                    connection.send(pooledReply);
                                } finally {
                                    pooledReply.free();
                                }
                                break;
                            }
                            case Protocol.MESSAGE_DATA: {
                                int channelId = buffer.getInt() ^ 0x80000000;
                                RemoteConnectionChannel connectionChannel = handler.getChannel(channelId);
                                if (connectionChannel == null) {
                                    connection.handleException(new IOException("Message data for non-existent channel"));
                                    break;
                                }
                                connectionChannel.handleMessageData(pooled);
                                // need a new buffer now
                                pooled = connection.allocate();
                                buffer = pooled.getResource();
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
                            case Protocol.MESSAGE_ASYNC_CLOSE: {
                                int channelId = buffer.getInt() ^ 0x80000000;
                                RemoteConnectionChannel connectionChannel = handler.getChannel(channelId);
                                if (connectionChannel == null) {
                                    break;
                                }
                                connectionChannel.handleAsyncClose(pooled);
                                break;
                            }
                            case Protocol.CHANNEL_CLOSED: {
                                int channelId = buffer.getInt() ^ 0x80000000;
                                RemoteConnectionChannel connectionChannel = handler.getChannel(channelId);
                                if (connectionChannel == null) {
                                    break;
                                }
                                connectionChannel.handleRemoteClose();
                                break;
                            }
                            case Protocol.CHANNEL_SHUTDOWN_WRITE: {
                                int channelId = buffer.getInt() ^ 0x80000000;
                                RemoteConnectionChannel connectionChannel = handler.getChannel(channelId);
                                if (connectionChannel == null) {
                                    break;
                                }
                                connectionChannel.handleWriteShutdown();
                                break;
                            }
                            case Protocol.CHANNEL_OPEN_ACK: {
                                int channelId = buffer.getInt() ^ 0x80000000;
                                if ((channelId & 0x80000000) == 0) {
                                    // invalid
                                    break;
                                }
                                PendingChannel pendingChannel = handler.removePendingChannel(channelId);
                                if (pendingChannel == null) {
                                    // invalid
                                    break;
                                }
                                int outboundWindow = pendingChannel.getOutboundWindowSize();
                                int inboundWindow = pendingChannel.getInboundWindowSize();
                                int outboundMessageCount = pendingChannel.getOutboundMessageCount();
                                int inboundMessageCount = pendingChannel.getInboundMessageCount();
                                OUT: for (;;) {
                                    switch (buffer.get() & 0xff) {
                                        case 0x80: {
                                            outboundWindow = ProtocolUtils.readInt(buffer);
                                            break;
                                        }
                                        case 0x81: {
                                            outboundMessageCount = ProtocolUtils.readUnsignedShort(buffer);
                                            break;
                                        }
                                        case 0x00: {
                                            break OUT;
                                        }
                                        default: {
                                            // ignore unknown parameter
                                            Buffers.skip(buffer, buffer.get() & 0xff);
                                            break;
                                        }
                                    }
                                }
                                RemoteConnectionChannel newChannel = new RemoteConnectionChannel(handler.getConnectionContext().getConnectionProviderContext().getExecutor(), connection, channelId, new Random(), outboundWindow, inboundWindow, outboundMessageCount, inboundMessageCount);
                                handler.putChannel(newChannel);
                                pendingChannel.getResult().setResult(newChannel);
                                break;
                            }
                            default: {
                                log.unknownProtocolId(protoId);
                                break;
                            }
                        }
                    } catch (BufferUnderflowException e) {
                        log.bufferUnderflow(protoId);
                    }
                } catch (BufferUnderflowException e) {
                    log.bufferUnderflowRaw();
                } finally {
                    buffer.clear();
                }
            } finally {
                pooled.free();
            }
        } catch (IOException e) {
            connection.handleException(e);
        }
    }
}
