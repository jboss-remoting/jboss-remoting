/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3.remote;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.charset.StandardCharsets;

import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.ServiceOpenException;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.RegisteredService;
import org.jboss.remoting3.spi.SpiUtils;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.Connection;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pooled;
import org.xnio.StreamConnection;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.sasl.SaslWrapper;

import static org.jboss.remoting3._private.Messages.log;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteReadListener implements ChannelListener<ConduitStreamSourceChannel> {

    private static final byte[] NO_BYTES = new byte[0];
    private final RemoteConnectionHandler handler;
    private final RemoteConnection connection;
    private ChannelListener previousCloseListener = null;

    RemoteReadListener(final RemoteConnectionHandler handler, final RemoteConnection connection) {
        Connection xnioConnection = connection.getConnection();
        if(xnioConnection instanceof StreamConnection) {
            previousCloseListener = ((StreamConnection) connection.getConnection()).getCloseListener();
        }
        synchronized (connection.getLock()) {
            connection.getConnection().getCloseSetter().set((ChannelListener<Channel>) channel -> connection.getExecutor().execute(() -> {
                handler.handleConnectionClose();
                handler.closeComplete();
                if(previousCloseListener != null) {
                    ChannelListeners.invokeChannelListener(channel, previousCloseListener);
                }
            }));
        }
        this.handler = handler;
        this.connection = connection;
    }

    public void handleEvent(final ConduitStreamSourceChannel channel) {
        SaslWrapper saslWrapper = connection.getSaslWrapper();
        final Object lock = connection.getLock();
        final MessageReader messageReader = connection.getMessageReader();
        try {
            Pooled<ByteBuffer> message = null;
            ByteBuffer buffer = null;
            for (;;) try {
                boolean exit = false;
                message = messageReader.getMessage();
                if (message == MessageReader.EOF_MARKER) {
                    log.trace("Received connection end-of-stream");
                    exit = true;
                } else if (message == null) {
                    log.trace("No message ready; returning");
                    return;
                }
                if (exit) {
                    messageReader.shutdownReads();
                    handler.receiveCloseRequest();
                    return;
                }
                buffer = message.getResource();
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
                            break;
                        }
                        case Protocol.CONNECTION_ALIVE_ACK: {
                            log.trace("Received connection alive ack");
                            break;
                        }
                        case Protocol.CONNECTION_CLOSE: {
                            log.trace("Received connection close request");
                            handler.receiveCloseRequest();
                            // do not return now so we can read once more,
                            // thus making sure we are not skipping a
                            // receive equal to -1
                            break;
                        }
                        case Protocol.CHANNEL_OPEN_REQUEST: {
                            log.trace("Received channel open request");
                            int channelId = buffer.getInt() ^ 0x80000000;
                            int requestedInboundWindow = Integer.MAX_VALUE;
                            int requestedInboundMessages = 0xffff;
                            int requestedOutboundWindow = Integer.MAX_VALUE;
                            int requestedOutboundMessages = 0xffff;
                            long requestedInboundMessageSize = Long.MAX_VALUE;
                            long requestedOutboundMessageSize = Long.MAX_VALUE;
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
                                        requestedOutboundWindow = Math.min(requestedOutboundWindow, ProtocolUtils.readInt(buffer));
                                        break;
                                    }
                                    case Protocol.O_MAX_INBOUND_MSG_COUNT: {
                                        requestedOutboundMessages = Math.min(requestedOutboundMessages, ProtocolUtils.readUnsignedShort(buffer));
                                        break;
                                    }
                                    case Protocol.O_MAX_OUTBOUND_MSG_WINDOW_SIZE: {
                                        requestedInboundWindow = Math.min(requestedInboundWindow, ProtocolUtils.readInt(buffer));
                                        break;
                                    }
                                    case Protocol.O_MAX_OUTBOUND_MSG_COUNT: {
                                        requestedInboundMessages = Math.min(requestedInboundMessages, ProtocolUtils.readUnsignedShort(buffer));
                                        break;
                                    }
                                    case Protocol.O_MAX_INBOUND_MSG_SIZE: {
                                        requestedOutboundMessageSize = Math.min(requestedOutboundMessageSize, ProtocolUtils.readLong(buffer));
                                        break;
                                    }
                                    case Protocol.O_MAX_OUTBOUND_MSG_SIZE: {
                                        requestedInboundMessageSize = Math.min(requestedInboundMessageSize, ProtocolUtils.readLong(buffer));
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
                                refuseService(channelId, "Unknown service name " + serviceType);
                                break;
                            }
                            final OptionMap serviceOptionMap = registeredService.getOptionMap();

                            final int outboundWindowOptionValue = serviceOptionMap.get(RemotingOptions.TRANSMIT_WINDOW_SIZE, RemotingOptions.INCOMING_CHANNEL_DEFAULT_TRANSMIT_WINDOW_SIZE);
                            final int outboundMessagesOptionValue = serviceOptionMap.get(RemotingOptions.MAX_OUTBOUND_MESSAGES, RemotingOptions.INCOMING_CHANNEL_DEFAULT_MAX_OUTBOUND_MESSAGES);
                            final int inboundWindowOptionValue = serviceOptionMap.get(RemotingOptions.RECEIVE_WINDOW_SIZE, RemotingOptions.INCOMING_CHANNEL_DEFAULT_RECEIVE_WINDOW_SIZE);
                            final int inboundMessagesOptionValue = serviceOptionMap.get(RemotingOptions.MAX_INBOUND_MESSAGES, RemotingOptions.DEFAULT_MAX_INBOUND_MESSAGES);
                            final long outboundMessageSizeOptionValue = serviceOptionMap.get(RemotingOptions.MAX_OUTBOUND_MESSAGE_SIZE, RemotingOptions.DEFAULT_MAX_OUTBOUND_MESSAGE_SIZE);
                            final long inboundMessageSizeOptionValue = serviceOptionMap.get(RemotingOptions.MAX_INBOUND_MESSAGE_SIZE, RemotingOptions.DEFAULT_MAX_INBOUND_MESSAGE_SIZE);

                            final int outboundWindow = Math.min(requestedOutboundWindow, outboundWindowOptionValue);
                            final int outboundMessages = Math.min(requestedOutboundMessages, outboundMessagesOptionValue);
                            final int inboundWindow = Math.min(requestedInboundWindow, inboundWindowOptionValue);
                            final int inboundMessages = Math.min(requestedInboundMessages, inboundMessagesOptionValue);
                            final long outboundMessageSize = Math.min(requestedOutboundMessageSize, outboundMessageSizeOptionValue);
                            final long inboundMessageSize = Math.min(requestedInboundMessageSize, inboundMessageSizeOptionValue);

                            if (log.isTraceEnabled()) {
                                log.tracef(
                                    "Inbound service request for channel %08x is configured as follows:\n" +
                                    "  outbound window:  req %10d, option %10d, grant %10d\n" +
                                    "  inbound window:   req %10d, option %10d, grant %10d\n" +
                                    "  outbound msgs:    req %10d, option %10d, grant %10d\n" +
                                    "  inbound msgs:     req %10d, option %10d, grant %10d\n" +
                                    "  outbound msgsize: req %19d, option %19d, grant %19d\n" +
                                    "  inbound msgsize:  req %19d, option %19d, grant %19d",
                                    Integer.valueOf(channelId),
                                    Integer.valueOf(requestedOutboundWindow), Integer.valueOf(outboundWindowOptionValue), Integer.valueOf(outboundWindow),
                                    Integer.valueOf(requestedInboundWindow), Integer.valueOf(inboundWindowOptionValue), Integer.valueOf(inboundWindow),
                                    Integer.valueOf(requestedOutboundMessages), Integer.valueOf(outboundMessagesOptionValue), Integer.valueOf(outboundMessages),
                                    Integer.valueOf(requestedInboundMessages), Integer.valueOf(inboundMessagesOptionValue), Integer.valueOf(inboundMessages),
                                    Long.valueOf(requestedOutboundMessageSize), Long.valueOf(outboundMessageSizeOptionValue), Long.valueOf(outboundMessageSize),
                                    Long.valueOf(requestedInboundMessageSize), Long.valueOf(inboundMessageSizeOptionValue), Long.valueOf(inboundMessageSize)
                                );
                            }

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
                                    if (inboundMessageSize != Long.MAX_VALUE) {
                                        ProtocolUtils.writeLong(replyBuffer, Protocol.O_MAX_INBOUND_MSG_SIZE, inboundMessageSize);
                                    }
                                    ProtocolUtils.writeInt(replyBuffer, Protocol.O_MAX_OUTBOUND_MSG_WINDOW_SIZE, outboundWindow);
                                    ProtocolUtils.writeShort(replyBuffer, Protocol.O_MAX_OUTBOUND_MSG_COUNT, outboundMessages);
                                    if (outboundMessageSize != Long.MAX_VALUE) {
                                        ProtocolUtils.writeLong(replyBuffer, Protocol.O_MAX_OUTBOUND_MSG_SIZE, outboundMessageSize);
                                    }
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
                            // protect against double-free if the method fails
                            Pooled<ByteBuffer> messageCopy = message;
                            message = null;
                            buffer = null;
                            connectionChannel.handleMessageData(messageCopy);
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
                            connectionChannel.handleWindowOpen(message);
                            break;
                        }
                        case Protocol.MESSAGE_CLOSE: {
                            log.trace("Received message async close");
                            int channelId = buffer.getInt() ^ 0x80000000;
                            RemoteConnectionChannel connectionChannel = handler.getChannel(channelId);
                            if (connectionChannel == null) {
                                break;
                            }
                            connectionChannel.handleAsyncClose(message);
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
                            int requestedOutboundWindow = pendingChannel.getOutboundWindowSize();
                            int requestedInboundWindow = pendingChannel.getInboundWindowSize();
                            int requestedOutboundMessageCount = pendingChannel.getOutboundMessageCount();
                            int requestedInboundMessageCount = pendingChannel.getInboundMessageCount();
                            long requestedOutboundMessageSize = pendingChannel.getOutboundMessageSize();
                            long requestedInboundMessageSize = pendingChannel.getInboundMessageSize();

                            int outboundWindow = requestedOutboundWindow;
                            int inboundWindow = requestedInboundWindow;
                            int outboundMessageCount = requestedOutboundMessageCount;
                            int inboundMessageCount = requestedInboundMessageCount;
                            long outboundMessageSize = requestedOutboundMessageSize;
                            long inboundMessageSize = requestedInboundMessageSize;

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

                            if (log.isTraceEnabled()) {
                                log.tracef(
                                    "Inbound service acknowledgement for channel %08x is configured as follows:\n" +
                                    "  outbound window:  req %10d, use %10d\n" +
                                    "  inbound window:   req %10d, use %10d\n" +
                                    "  outbound msgs:    req %10d, use %10d\n" +
                                    "  inbound msgs:     req %10d, use %10d\n" +
                                    "  outbound msgsize: req %19d, use %19d\n" +
                                    "  inbound msgsize:  req %19d, use %19d",
                                    Integer.valueOf(channelId),
                                    Integer.valueOf(requestedOutboundWindow), Integer.valueOf(outboundWindow),
                                    Integer.valueOf(requestedInboundWindow), Integer.valueOf(inboundWindow),
                                    Integer.valueOf(requestedOutboundMessageCount), Integer.valueOf(outboundMessageCount),
                                    Integer.valueOf(requestedInboundMessageCount), Integer.valueOf(inboundMessageCount),
                                    Long.valueOf(requestedOutboundMessageSize), Long.valueOf(outboundMessageSize),
                                    Long.valueOf(requestedInboundMessageSize), Long.valueOf(inboundMessageSize)
                                );
                            }

                            RemoteConnectionChannel newChannel = new RemoteConnectionChannel(handler, connection, channelId, outboundWindow, inboundWindow, outboundMessageCount, inboundMessageCount, outboundMessageSize, inboundMessageSize);
                            handler.putChannel(newChannel);
                            pendingChannel.getResult().setResult(newChannel);
                            break;
                        }
                        case Protocol.SERVICE_ERROR: {
                            log.trace("Received service error");
                            int channelId = buffer.getInt() ^ 0x80000000;
                            handler.handleOutboundChannelClosed();
                            PendingChannel pendingChannel = handler.removePendingChannel(channelId);
                            if (pendingChannel == null) {
                                // invalid
                                break;
                            }
                            String reason = new String(Buffers.take(buffer), StandardCharsets.UTF_8);
                            pendingChannel.getResult().setException(new ServiceOpenException(reason));
                            break;
                        }
                        case Protocol.APP_AUTH_REQUEST: {
                            int id = buffer.getInt();
                            final int length = (buffer.get() - 1 & 0xff) + 1; // range: 1 - 256
                            final byte[] mechNameBytes = new byte[length];
                            buffer.get(mechNameBytes);
                            String mechName = new String(mechNameBytes, StandardCharsets.UTF_8);
                            log.tracef("Received authentication request, id %08x, mech %s", id, mechName);
                            ConnectionHandlerContext c = handler.getConnectionContext();
                            final byte[] saslBytes;
                            if (buffer.hasRemaining()) {
                                saslBytes = new byte[buffer.remaining()];
                                buffer.get(saslBytes);
                            } else {
                                saslBytes = NO_BYTES;
                            }
                            c.receiveAuthRequest(id, mechName, saslBytes);
                            break;
                        }
                        case Protocol.APP_AUTH_CHALLENGE: {
                            int id = buffer.getInt();
                            log.tracef("Received authentication challenge, id %08x", id);
                            ConnectionHandlerContext c = handler.getConnectionContext();
                            final byte[] saslBytes;
                            saslBytes = new byte[buffer.remaining()];
                            buffer.get(saslBytes);
                            c.receiveAuthChallenge(id, saslBytes);
                            break;
                        }
                        case Protocol.APP_AUTH_RESPONSE: {
                            int id = buffer.getInt();
                            log.tracef("Received authentication response, id %08x", id);
                            ConnectionHandlerContext c = handler.getConnectionContext();
                            final byte[] saslBytes;
                            if (buffer.hasRemaining()) {
                                saslBytes = new byte[buffer.remaining()];
                                buffer.get(saslBytes);
                            } else {
                                saslBytes = NO_BYTES;
                            }
                            c.receiveAuthResponse(id, saslBytes);
                            break;
                        }
                        case Protocol.APP_AUTH_SUCCESS: {
                            int id = buffer.getInt();
                            log.tracef("Received authentication success, id %08x", id);
                            ConnectionHandlerContext c = handler.getConnectionContext();
                            final byte[] saslBytes;
                            if (buffer.hasRemaining()) {
                                saslBytes = new byte[buffer.remaining()];
                                buffer.get(saslBytes);
                            } else {
                                saslBytes = NO_BYTES;
                            }
                            c.receiveAuthSuccess(id, saslBytes);
                            break;
                        }
                        case Protocol.APP_AUTH_REJECT: {
                            int id = buffer.getInt();
                            log.tracef("Received authentication reject, id %08x", id);
                            ConnectionHandlerContext c = handler.getConnectionContext();
                            c.receiveAuthReject(id);
                            break;
                        }
                        case Protocol.APP_AUTH_DELETE: {
                            int id = buffer.getInt();
                            log.tracef("Received authentication delete, id %08x", id);
                            ConnectionHandlerContext c = handler.getConnectionContext();
                            c.receiveAuthDelete(id);
                            break;
                        }
                        case Protocol.APP_AUTH_DELETE_ACK: {
                            int id = buffer.getInt();
                            log.tracef("Received authentication delete ack, id %08x", id);
                            ConnectionHandlerContext c = handler.getConnectionContext();
                            c.receiveAuthDeleteAck(id);
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
                if (message != null) message.free();
            }
        } catch (IOException e) {
            connection.handleException(e);
            synchronized (lock) {
                IoUtils.safeClose(channel);
            }
        }
    }

    private void refuseService(final int channelId, final String reason) {
        if (log.isTraceEnabled()) {
            log.tracef("Refusing service on channel %08x: %s", Integer.valueOf(channelId), reason);
        }
        Pooled<ByteBuffer> pooledReply = connection.allocate();
        boolean ok = false;
        try {
            ByteBuffer replyBuffer = pooledReply.getResource();
            replyBuffer.clear();
            replyBuffer.put(Protocol.SERVICE_ERROR);
            replyBuffer.putInt(channelId);
            replyBuffer.put(reason.getBytes(StandardCharsets.UTF_8));
            replyBuffer.flip();
            ok = true;
            // send takes ownership of the buffer
            connection.send(pooledReply);
        } finally {
            if (! ok) pooledReply.free();
        }
    }
}
