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
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.Random;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.ServiceOpenException;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.xnio.Cancellable;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pooled;
import org.xnio.Result;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedMessageChannel;

final class RemoteConnectionHandler implements ConnectionHandler {

    static final int LENGTH_PLACEHOLDER = 0;

    private final ConnectionHandlerContext connectionContext;
    private final RemoteConnection remoteConnection;
    private final Random random = new Random();

    /**
     * Channels.  Remote channel IDs are read with a "1" MSB and written with a "0" MSB.
     * Local channel IDs are read with a "0" MSB and written with a "1" MSB.  Channel IDs here
     * are stored from the "write" perspective.  Remote channels "0", Local channels "1" MSB.
     */
    private final UnlockedReadIntIndexHashMap<RemoteConnectionChannel> channels = new UnlockedReadIntIndexHashMap<RemoteConnectionChannel>(RemoteConnectionChannel.INDEXER);
    /**
     * Pending channels.  All have a "1" MSB.  Replies are read with a "0" MSB.
     */
    private final UnlockedReadIntIndexHashMap<PendingChannel> pendingChannels = new UnlockedReadIntIndexHashMap<PendingChannel>(PendingChannel.INDEXER);

    // todo limit or whatever
    private int channelCount = 50;

    RemoteConnectionHandler(final ConnectionHandlerContext connectionContext, final RemoteConnection remoteConnection) {
        this.connectionContext = connectionContext;
        this.remoteConnection = remoteConnection;
    }

    public Cancellable open(final String serviceType, final Result<Channel> result, final OptionMap optionMap) {
        byte[] serviceTypeBytes = serviceType.getBytes(Protocol.UTF_8);
        final int serviceTypeLength = serviceTypeBytes.length;
        if (serviceTypeLength > 255) {
            result.setException(new ServiceOpenException("Service type name is too long"));
            return IoUtils.nullCancellable();
        }

        int id;
        final OptionMap connectionOptionMap = remoteConnection.getOptionMap();

        final int outboundWindowSize = optionMap.get(RemotingOptions.TRANSMIT_WINDOW_SIZE, connectionOptionMap.get(RemotingOptions.TRANSMIT_WINDOW_SIZE, Protocol.DEFAULT_WINDOW_SIZE));
        final int inboundWindowSize = optionMap.get(RemotingOptions.RECEIVE_WINDOW_SIZE, connectionOptionMap.get(RemotingOptions.RECEIVE_WINDOW_SIZE, Protocol.DEFAULT_WINDOW_SIZE));
        final int outboundMessageCount = optionMap.get(RemotingOptions.MAX_OUTBOUND_MESSAGES, connectionOptionMap.get(RemotingOptions.MAX_OUTBOUND_MESSAGES, Protocol.DEFAULT_MESSAGE_COUNT));
        final int inboundMessageCount = optionMap.get(RemotingOptions.MAX_INBOUND_MESSAGES, connectionOptionMap.get(RemotingOptions.MAX_INBOUND_MESSAGES, Protocol.DEFAULT_MESSAGE_COUNT));
        UnlockedReadIntIndexHashMap<PendingChannel> pendingChannels = this.pendingChannels;
        synchronized (this) {
            int channelCount;
            while ((channelCount = this.channelCount) == 0) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    result.setException(new InterruptedIOException("Interrupted while waiting to write message"));
                    return IoUtils.nullCancellable();
                }
            }
            final Random random = this.random;
            for (;;) {
                id = random.nextInt() | 0x80000000;
                if (! pendingChannels.containsKey(id)) {
                    PendingChannel pendingChannel = new PendingChannel(id, outboundWindowSize, inboundWindowSize, outboundMessageCount, inboundMessageCount, result);
                    pendingChannels.put(pendingChannel);
                    this.channelCount = channelCount - 1;
                    // TODO send
                    Pooled<ByteBuffer> pooled = remoteConnection.allocate();
                    try {
                        ByteBuffer buffer = pooled.getResource();
                        ConnectedMessageChannel channel = remoteConnection.getChannel();
                        buffer.put(Protocol.CHANNEL_OPEN_REQUEST);
                        buffer.putInt(id);
                        ProtocolUtils.writeBytes(buffer, 1, serviceTypeBytes);
                        ProtocolUtils.writeInt(buffer, 0x80, inboundWindowSize);
                        ProtocolUtils.writeShort(buffer, 0x81, inboundMessageCount);
                        buffer.put((byte) 0);
                        buffer.flip();
                        try {
                            Channels.sendBlocking(channel, buffer);
                        } catch (IOException e) {
                            result.setException(e);
                            pendingChannels.remove(id);
                            return IoUtils.nullCancellable();
                        }
                        // TODO: allow cancel
                        return IoUtils.nullCancellable();
                    } finally {
                        pooled.free();
                    }
                }
            }
        }
    }

    public void close() throws IOException {
        remoteConnection.handleException(new ClosedChannelException());
    }

    ConnectionHandlerContext getConnectionContext() {
        return connectionContext;
    }

    Random getRandom() {
        return random;
    }

    RemoteConnection getRemoteConnection() {
        return remoteConnection;
    }

    private static final ThreadLocal<RemoteConnectionHandler> current = new ThreadLocal<RemoteConnectionHandler>();

    static RemoteConnectionHandler getCurrent() {
        return current.get();
    }

    static RemoteConnectionHandler setCurrent(RemoteConnectionHandler newCurrent) {
        final ThreadLocal<RemoteConnectionHandler> current = RemoteConnectionHandler.current;
        try {
            return current.get();
        } finally {
            current.set(newCurrent);
        }
    }

    void addChannel(final RemoteConnectionChannel channel) {
        RemoteConnectionChannel existing = channels.putIfAbsent(channel);
        if (existing != null) {
            // should not be possible...
            channel.getConnection().handleException(new IOException("Attempted to add an already-existing channel"));
        }
    }

    RemoteConnectionChannel getChannel(final int id) {
        return channels.get(id);
    }

    PendingChannel removePendingChannel(final int id) {
        return pendingChannels.remove(id);
    }

    void putChannel(final RemoteConnectionChannel channel) {
        channels.put(channel);
    }
}
