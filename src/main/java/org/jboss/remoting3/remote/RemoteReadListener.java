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

import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.spi.RegisteredService;
import org.jboss.remoting3.spi.SpiUtils;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Pooled;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.sasl.SaslWrapper;

import static org.jboss.remoting3.remote.RemoteLogger.log;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteReadListener implements ChannelListener<ConnectedMessageChannel> {

    private final RemoteConnectionHandler handler;
    private final RemoteConnection connection;

    RemoteReadListener(final RemoteConnectionHandler handler, final RemoteConnection connection) {
        connection.getChannel().getCloseSetter().set(new ChannelListener<java.nio.channels.Channel>() {
            public void handleEvent(final java.nio.channels.Channel channel) {
                connection.getExecutor().execute(new Runnable() {
                    public void run() {
                        handler.handleConnectionClose();
                    }
                });
            }
        });
        this.handler = handler;
        this.connection = connection;
    }

    public void handleEvent(final ConnectedMessageChannel channel) {
        int res;
        SaslWrapper saslWrapper = connection.getSaslWrapper();
        try {
            Pooled<ByteBuffer> pooled = connection.allocate();
            ByteBuffer buffer = pooled.getResource();
            try {
                for (;;) try {
                    res = channel.receive(buffer);
                    if (res == -1) {
                        log.trace("Received connection end-of-stream");
                        try {
                            channel.shutdownReads();
                        } finally {
                            handler.handleConnectionClose();
                        }
                        return;
                    } else if (res == 0) {
                        log.trace("No message ready; returning");
                        return;
                    }
                    buffer.flip();
                    if (saslWrapper != null) {
                        final ByteBuffer source = buffer.duplicate();
                        buffer.clear();
                        saslWrapper.unwrap(buffer, source);
                        buffer.flip();
                    }
                    final byte protoId = buffer.get();
                    try {
                        switch (protoId) {
                            case Protocol.CONNECTION_ALIVE: {
                                log.trace("Received connection alive");
                                connection.sendAliveResponse();
                                return;
                            }
                            case Protocol.CONNECTION_ALIVE_ACK: {
                                log.trace("Received connection alive ack");
                                return;
                            }
                            case Protocol.CONNECTION_CLOSE: {
                                log.trace("Received connection close request");
                                handler.receiveCloseRequest();
                                return;
                            }
                            case Protocol.CHANNEL_OPEN_REQUEST: {
                                log.trace("Received channel open request");
                                int channelId = buffer.getInt() ^ 0x80000000;
                                int inboundWindow = Integer.MAX_VALUE;
                                int inboundMessages = 0xffff;
                                int outboundWindow = Integer.MAX_VALUE;
                                int outboundMessages = 0xffff;
                                long inboundMessageSize = Long.MAX_VALUE;
                                long outboundMessageSize = Long.MAX_VALUE;
                                // parse out request
                                int b;
                                String serviceType = null;
                                OUT: for (;;) {
                                    b = buffer.get() & 0xff;
                                    switch (b) {
                                        case Protocol.O_END: break OUT;
                                        case Protocol.O_SERVICE_NAME: {
                                            serviceType = ProtocolUtils.readString(buffer);
                                            break;
                                        }
                                        case Protocol.O_MAX_INBOUND_MSG_WINDOW_SIZE: {
                                            outboundWindow = Math.min(outboundWindow, ProtocolUtils.readInt(buffer));
                                            break;
                                        }
                                        case Protocol.O_MAX_INBOUND_MSG_COUNT: {
                                            outboundMessages = Math.min(outboundMessages, ProtocolUtils.readUnsignedShort(buffer));
                                            break;
                                        }
                                        case Protocol.O_MAX_OUTBOUND_MSG_WINDOW_SIZE: {
                                            inboundWindow = Math.min(inboundWindow, ProtocolUtils.readInt(buffer));
                                            break;
                                        }
                                        case Protocol.O_MAX_OUTBOUND_MSG_COUNT: {
                                            inboundMessages = Math.min(inboundMessages, ProtocolUtils.readUnsignedShort(buffer));
                                            break;
                                        }
                                        case Protocol.O_MAX_INBOUND_MSG_SIZE: {
                                            outboundMessageSize = Math.min(outboundMessageSize, ProtocolUtils.readLong(buffer));
                                            break;
                                        }
                                        case Protocol.O_MAX_OUTBOUND_MSG_SIZE: {
                                            inboundMessageSize = Math.min(inboundMessageSize, ProtocolUtils.readLong(buffer));
                                            break;
                                        }
                                        default: {
                                            Buffers.skip(buffer, buffer.get() & 0xff);
                                            break;
                                        }
                                    }
                                }
                                if ((channelId & 0x80000000) != 0) {
                                    // invalid channel ID, original should have had MSB=1 and thus the complement should be MSB=0
                                    refuseService(channelId, "Invalid channel ID");
                                    break;
                                }

                                if (serviceType == null) {
                                    // invalid service reply
                                    refuseService(channelId, "Missing service name");
                                    break;
                                }

                                final RegisteredService registeredService = handler.getConnectionContext().getRegisteredService(serviceType);
                                if (registeredService == null) {
                                    refuseService(channelId, "Unknown service name");
                                    break;
                                }
                                final OptionMap serviceOptionMap = registeredService.getOptionMap();
                                outboundWindow = Math.min(outboundWindow, serviceOptionMap.get(RemotingOptions.TRANSMIT_WINDOW_SIZE, Protocol.DEFAULT_WINDOW_SIZE));
                                outboundMessages = Math.min(outboundMessages, serviceOptionMap.get(RemotingOptions.MAX_OUTBOUND_MESSAGES, Protocol.DEFAULT_MESSAGE_COUNT));
                                inboundWindow = Math.min(inboundWindow, serviceOptionMap.get(RemotingOptions.RECEIVE_WINDOW_SIZE, Protocol.DEFAULT_WINDOW_SIZE));
                                inboundMessages = Math.min(inboundMessages, serviceOptionMap.get(RemotingOptions.MAX_INBOUND_MESSAGES, Protocol.DEFAULT_MESSAGE_COUNT));
                                outboundMessageSize = Math.min(outboundMessageSize, serviceOptionMap.get(RemotingOptions.MAX_OUTBOUND_MESSAGE_SIZE, Long.MAX_VALUE));
                                inboundMessageSize = Math.min(inboundMessageSize, serviceOptionMap.get(RemotingOptions.MAX_INBOUND_MESSAGE_SIZE, Long.MAX_VALUE));

                                final OpenListener openListener = registeredService.getOpenListener();
                                if (! handler.handleInboundChannelOpen()) {
                                    // refuse
                                    refuseService(channelId, "Channel refused");
                                    break;
                                }
                                boolean ok1 = false;
                                try {
                                    // construct the channel
                                    RemoteConnectionChannel connectionChannel = new RemoteConnectionChannel(handler, connection, channelId, outboundWindow, inboundWindow, outboundMessages, inboundMessages, outboundMessageSize, inboundMessageSize);
                                    RemoteConnectionChannel existing = handler.addChannel(connectionChannel);
                                    if (existing != null) {
                                        log.tracef("Encountered open request for duplicate %s", existing);
                                        // the channel already exists, which means the remote side "forgot" about it or we somehow missed the close message.
                                        // the only safe thing to do is to terminate the existing channel.
                                        try {
                                            refuseService(channelId, "Duplicate ID");
                                        } finally {
                                            existing.handleRemoteClose();
                                        }
                                        break;
                                    }

                                    // construct reply
                                    Pooled<ByteBuffer> pooledReply = connection.allocate();
                                    boolean ok2 = false;
                                    try {
                                        ByteBuffer replyBuffer = pooledReply.getResource();
                                        replyBuffer.clear();
                                        replyBuffer.put(Protocol.CHANNEL_OPEN_ACK);
                                        replyBuffer.putInt(channelId);
                                        ProtocolUtils.writeInt(replyBuffer, Protocol.O_MAX_INBOUND_MSG_WINDOW_SIZE, inboundWindow);
                                        ProtocolUtils.writeShort(replyBuffer, Protocol.O_MAX_INBOUND_MSG_COUNT, inboundMessages);
                                        ProtocolUtils.writeLong(replyBuffer, Protocol.O_MAX_INBOUND_MSG_SIZE, inboundMessageSize);
                                        ProtocolUtils.writeInt(replyBuffer, Protocol.O_MAX_OUTBOUND_MSG_WINDOW_SIZE, outboundWindow);
                                        ProtocolUtils.writeShort(replyBuffer, Protocol.O_MAX_OUTBOUND_MSG_COUNT, outboundWindow);
                                        ProtocolUtils.writeLong(replyBuffer, Protocol.O_MAX_OUTBOUND_MSG_COUNT, outboundMessageSize);
                                        replyBuffer.put((byte) 0);
                                        replyBuffer.flip();
                                        ok2 = true;
                                        // send takes ownership of the buffer
                                        connection.send(pooledReply);
                                    } finally {
                                        if (! ok2) pooledReply.free();
                                    }

                                    ok1 = true;

                                    // Call the service open listener
                                    connection.getExecutor().execute(SpiUtils.getServiceOpenTask(connectionChannel, openListener));
                                    break;
                                } finally {
                                    // the inbound channel wasn't open so don't leak the ref count
                                    if (! ok1) handler.handleInboundChannelClosed();
                                }
                            }
                            case Protocol.MESSAGE_DATA: {
                                log.trace("Received message data");
                                int channelId = buffer.getInt() ^ 0x80000000;
                                RemoteConnectionChannel connectionChannel = handler.getChannel(channelId);
                                if (connectionChannel == null) {
                                    // ignore the data
                                    log.tracef("Ignoring message data for expired channel");
                                    break;
                                }
                                connectionChannel.handleMessageData(pooled);
                                // need a new buffer now
                                pooled = connection.allocate();
                                buffer = pooled.getResource();
                                break;
                            }
                            case Protocol.MESSAGE_WINDOW_OPEN: {
                                log.trace("Received message window open");
                                int channelId = buffer.getInt() ^ 0x80000000;
                                RemoteConnectionChannel connectionChannel = handler.getChannel(channelId);
                                if (connectionChannel == null) {
                                    // ignore
                                    log.tracef("Ignoring window open for expired channel");
                                    break;
                                }
                                connectionChannel.handleWindowOpen(pooled);
                                break;
                            }
                            case Protocol.MESSAGE_CLOSE: {
                                log.trace("Received message async close");
                                int channelId = buffer.getInt() ^ 0x80000000;
                                RemoteConnectionChannel connectionChannel = handler.getChannel(channelId);
                                if (connectionChannel == null) {
                                    break;
                                }
                                connectionChannel.handleAsyncClose(pooled);
                                break;
                            }
                            case Protocol.CHANNEL_CLOSED: {
                                log.trace("Received channel closed");
                                int channelId = buffer.getInt() ^ 0x80000000;
                                RemoteConnectionChannel connectionChannel = handler.getChannel(channelId);
                                if (connectionChannel == null) {
                                    break;
                                }
                                connectionChannel.handleRemoteClose();
                                break;
                            }
                            case Protocol.CHANNEL_SHUTDOWN_WRITE: {
                                log.trace("Received channel shutdown write");
                                int channelId = buffer.getInt() ^ 0x80000000;
                                RemoteConnectionChannel connectionChannel = handler.getChannel(channelId);
                                if (connectionChannel == null) {
                                    break;
                                }
                                connectionChannel.handleIncomingWriteShutdown();
                                break;
                            }
                            case Protocol.CHANNEL_OPEN_ACK: {
                                log.trace("Received channel open ack");
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
                                long outboundMessageSize = pendingChannel.getOutboundMessageSize();
                                long inboundMessageSize = pendingChannel.getInboundMessageSize();
                                OUT: for (;;) {
                                    switch (buffer.get() & 0xff) {
                                        case Protocol.O_MAX_INBOUND_MSG_WINDOW_SIZE: {
                                            outboundWindow = Math.min(outboundWindow, ProtocolUtils.readInt(buffer));
                                            break;
                                        }
                                        case Protocol.O_MAX_INBOUND_MSG_COUNT: {
                                            outboundMessageCount = Math.min(outboundMessageCount, ProtocolUtils.readUnsignedShort(buffer));
                                            break;
                                        }
                                        case Protocol.O_MAX_OUTBOUND_MSG_WINDOW_SIZE: {
                                            inboundWindow = Math.min(inboundWindow, ProtocolUtils.readInt(buffer));
                                            break;
                                        }
                                        case Protocol.O_MAX_OUTBOUND_MSG_COUNT: {
                                            inboundMessageCount = Math.min(inboundMessageCount, ProtocolUtils.readUnsignedShort(buffer));
                                            break;
                                        }
                                        case Protocol.O_MAX_INBOUND_MSG_SIZE: {
                                            outboundMessageSize = Math.min(outboundMessageSize, ProtocolUtils.readLong(buffer));
                                            break;
                                        }
                                        case Protocol.O_MAX_OUTBOUND_MSG_SIZE: {
                                            inboundMessageSize = Math.min(inboundMessageSize, ProtocolUtils.readLong(buffer));
                                            break;
                                        }
                                        case Protocol.O_END: {
                                            break OUT;
                                        }
                                        default: {
                                            // ignore unknown parameter
                                            Buffers.skip(buffer, buffer.get() & 0xff);
                                            break;
                                        }
                                    }
                                }
                                RemoteConnectionChannel newChannel = new RemoteConnectionChannel(handler, connection, channelId, outboundWindow, inboundWindow, outboundMessageCount, inboundMessageCount, outboundMessageSize, inboundMessageSize);
                                handler.putChannel(newChannel);
                                pendingChannel.getResult().setResult(newChannel);
                                break;
                            }
                            case Protocol.SERVICE_ERROR: {
                                log.trace("Received service error");
                                int channelId = buffer.getInt() ^ 0x80000000;
                                PendingChannel pendingChannel = handler.removePendingChannel(channelId);
                                if (pendingChannel == null) {
                                    // invalid
                                    break;
                                }
                                String reason = new String(Buffers.take(buffer), Protocol.UTF_8);
                                pendingChannel.getResult().setException(new IOException(reason));
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
            handler.handleConnectionClose();
        }
    }

    private void refuseService(final int channelId, final String reason) {
        Pooled<ByteBuffer> pooledReply = connection.allocate();
        boolean ok = false;
        try {
            ByteBuffer replyBuffer = pooledReply.getResource();
            replyBuffer.clear();
            replyBuffer.put(Protocol.SERVICE_ERROR);
            replyBuffer.putInt(channelId);
            replyBuffer.put(reason.getBytes(Protocol.UTF_8));
            replyBuffer.flip();
            ok = true;
            // send takes ownership of the buffer
            connection.send(pooledReply);
        } finally {
            if (! ok) pooledReply.free();
        }
    }
}
