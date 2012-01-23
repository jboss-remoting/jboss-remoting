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
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.NotOpenException;
import org.jboss.remoting3.ProtocolException;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.ServiceOpenException;
import org.jboss.remoting3.security.UserInfo;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.xnio.Cancellable;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pooled;
import org.xnio.Result;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedMessageChannel;

final class RemoteConnectionHandler extends AbstractHandleableCloseable<ConnectionHandler> implements ConnectionHandler {

    private static final RemoteLogger log = RemoteLogger.log;

    private final ConnectionHandlerContext connectionContext;
    private final RemoteConnection remoteConnection;

    /**
     * Channels.  Remote channel IDs are read with a "1" MSB and written with a "0" MSB.
     * Local channel IDs are read with a "0" MSB and written with a "1" MSB.  Channel IDs here
     * are stored from the "write" perspective.  Remote channels "0", Local channels "1" MSB.
     */
    private final IntIndexMap<RemoteConnectionChannel> channels = new IntIndexHashMap<RemoteConnectionChannel>(RemoteConnectionChannel.INDEXER, Equaller.IDENTITY);
    /**
     * Pending channels.  All have a "1" MSB.  Replies are read with a "0" MSB.
     */
    private final IntIndexMap<PendingChannel> pendingChannels = new IntIndexHashMap<PendingChannel>(PendingChannel.INDEXER, Equaller.IDENTITY);
    private final Collection<Principal> principals;

    private final int maxInboundChannels = 40;
    private final int maxOutboundChannels = 40;

    private final String remoteEndpointName;

    private volatile int channelState = 0;

    private static final AtomicIntegerFieldUpdater<RemoteConnectionHandler> channelStateUpdater = AtomicIntegerFieldUpdater.newUpdater(RemoteConnectionHandler.class, "channelState");

    /** Sending close request, now shutting down the write side of all channels and refusing new channels. Once send, received = true and count == 0, shut down writes on the socket. */
    private static final int SENT_CLOSE_REQ = (1 << 31);
    /** Received close request.  Send a close req if we haven't already done so. */
    private static final int RECEIVED_CLOSE_REQ = (1 << 30);
    private static final int OUTBOUND_CHANNELS_MASK = (1 << 15) - 1;
    private static final int ONE_OUTBOUND_CHANNEL = 1;
    private static final int INBOUND_CHANNELS_MASK = ((1 << 30) - 1) & ~OUTBOUND_CHANNELS_MASK;
    private static final int ONE_INBOUND_CHANNEL = (1 << 15);

    RemoteConnectionHandler(final ConnectionHandlerContext connectionContext, final RemoteConnection remoteConnection, final UserInfo userInfo, final String remoteEndpointName) {
        super(remoteConnection.getExecutor());
        this.connectionContext = connectionContext;
        this.remoteConnection = remoteConnection;
        this.remoteEndpointName = remoteEndpointName;

        this.principals = Collections.unmodifiableCollection(userInfo.getPrincipals());
    }

    /**
     * The socket channel was closed with or without our consent.
     */
    void handleConnectionClose() {
        closePendingChannels();
        closeAllChannels();
        remoteConnection.shutdownWrites();
        try {
            closeAction();
        } catch (IOException ignored) {
            log.tracef(ignored, "Failure to close after forced connection close");
        }
        closeComplete();
    }

    /**
     * A channel was closed, locally or remotely.
     *
     * @param channel the channel that was closed
     */
    void handleChannelClosed(RemoteConnectionChannel channel) {
        int channelId = channel.getChannelId();
        channels.remove(channel);
        boolean inbound = (channelId & 0x80000000) == 0;
        if (inbound) {
            handleInboundChannelClosed();
        } else {
            handleOutboundChannelClosed();
        }
    }

    void handleInboundChannelClosed() {
        int oldState;
        oldState = incrementState(-ONE_INBOUND_CHANNEL);
        if (oldState == (SENT_CLOSE_REQ | RECEIVED_CLOSE_REQ)) {
            log.tracef("Closed inbound channel on %s (shutting down)", this);
            remoteConnection.shutdownWrites();
        } else {
            log.tracef("Closed inbound channel on %s", this);
        }
    }

    void handleOutboundChannelClosed() {
        int oldState;
        oldState = incrementState(-ONE_OUTBOUND_CHANNEL);
        if (oldState == (SENT_CLOSE_REQ | RECEIVED_CLOSE_REQ)) {
            log.tracef("Closed outbound channel on %s (shutting down)", this);
            remoteConnection.shutdownWrites();
        } else {
            log.tracef("Closed outbound channel on %s", this);
        }
    }

    boolean handleInboundChannelOpen() {
        int oldState, newState;
        do {
            oldState = channelState;
            int oldCount = oldState & INBOUND_CHANNELS_MASK;
            if (oldCount == maxInboundChannels) {
                log.tracef("Refused inbound channel request on %s because too many inbound channels are open");
                return false;
            }
            if ((oldState & SENT_CLOSE_REQ) != 0) {
                log.tracef("Refused inbound channel request on %s because close request was sent");
                return false;
            }
            newState = oldState + ONE_INBOUND_CHANNEL;
        } while (!casState(oldState, newState));
        log.tracef("Opened inbound channel on %s", this);
        return true;
    }

    void handleOutboundChannelOpen() throws IOException {
        int oldState, newState;
        do {
            oldState = channelState;
            int oldCount = oldState & OUTBOUND_CHANNELS_MASK;
            if (oldCount == maxOutboundChannels) {
                log.tracef("Refused outbound channel open on %s because too many outbound channels are open");
                throw new ProtocolException("Too many channels open");
            }
            if ((oldState & SENT_CLOSE_REQ) != 0) {
                log.tracef("Refused outbound channel open on %s because close request was sent");
                throw new NotOpenException("Cannot open new channel because close was initiated");
            }
            newState = oldState + ONE_OUTBOUND_CHANNEL;
        } while (!casState(oldState, newState));
        log.tracef("Opened outbound channel on %s", this);
    }

    /**
     * The remote side requests a close of the whole channel.
     */
    void receiveCloseRequest() {
        int oldState, newState;
        do {
            oldState = channelState;
            if ((oldState & RECEIVED_CLOSE_REQ) != 0) {
                // ignore duplicate, weird though it may be
                return;
            }
            newState = oldState | RECEIVED_CLOSE_REQ | SENT_CLOSE_REQ;
        } while (!casState(oldState, newState));
        closePendingChannels();
        log.tracef("Received remote close request on %s", this);
        if ((oldState & SENT_CLOSE_REQ) == 0) {
            sendCloseRequestBody();
            closeAllChannels();
        }
        if ((oldState & (INBOUND_CHANNELS_MASK | OUTBOUND_CHANNELS_MASK)) == 0) {
            remoteConnection.shutdownWrites();
        }
    }

    void sendCloseRequest() {
        int oldState, newState;
        do {
            oldState = channelState;
            if ((oldState & SENT_CLOSE_REQ) != 0) {
                // idempotent close
                return;
            }
            newState = oldState | SENT_CLOSE_REQ;
        } while (!casState(oldState, newState));
        log.tracef("Sending close request on %s", this);
        sendCloseRequestBody();
        closeAllChannels();
        if ((oldState & (INBOUND_CHANNELS_MASK | OUTBOUND_CHANNELS_MASK)) == 0) {
            remoteConnection.shutdownWrites();
        }
    }

    private int incrementState(final int count) {
        final int oldState = channelStateUpdater.getAndAdd(this, count);
        if (log.isTraceEnabled()) {
            final int newState = oldState + count;
            log.tracef("CAS %s\n\told: RS=%s WS=%s IC=%d OC=%d\n\tnew: RS=%s WS=%s IC=%d OC=%d", this,
                    Boolean.valueOf((oldState & RECEIVED_CLOSE_REQ) != 0),
                    Boolean.valueOf((oldState & SENT_CLOSE_REQ) != 0),
                    Integer.valueOf((oldState & INBOUND_CHANNELS_MASK) >> Integer.numberOfTrailingZeros(ONE_INBOUND_CHANNEL)),
                    Integer.valueOf((oldState & OUTBOUND_CHANNELS_MASK) >> Integer.numberOfTrailingZeros(ONE_OUTBOUND_CHANNEL)),
                    Boolean.valueOf((newState & RECEIVED_CLOSE_REQ) != 0),
                    Boolean.valueOf((newState & SENT_CLOSE_REQ) != 0),
                    Integer.valueOf((newState & INBOUND_CHANNELS_MASK) >> Integer.numberOfTrailingZeros(ONE_INBOUND_CHANNEL)),
                    Integer.valueOf((newState & OUTBOUND_CHANNELS_MASK) >> Integer.numberOfTrailingZeros(ONE_OUTBOUND_CHANNEL))
                    );
        }
        return oldState;
    }

    private boolean casState(final int oldState, final int newState) {
        final boolean result = channelStateUpdater.compareAndSet(this, oldState, newState);
        if (result && log.isTraceEnabled()) {
            log.tracef("CAS %s\n\told: RS=%s WS=%s IC=%d OC=%d\n\tnew: RS=%s WS=%s IC=%d OC=%d", this,
                    Boolean.valueOf((oldState & RECEIVED_CLOSE_REQ) != 0),
                    Boolean.valueOf((oldState & SENT_CLOSE_REQ) != 0),
                    Integer.valueOf((oldState & INBOUND_CHANNELS_MASK) >> Integer.numberOfTrailingZeros(ONE_INBOUND_CHANNEL)),
                    Integer.valueOf((oldState & OUTBOUND_CHANNELS_MASK) >> Integer.numberOfTrailingZeros(ONE_OUTBOUND_CHANNEL)),
                    Boolean.valueOf((newState & RECEIVED_CLOSE_REQ) != 0),
                    Boolean.valueOf((newState & SENT_CLOSE_REQ) != 0),
                    Integer.valueOf((newState & INBOUND_CHANNELS_MASK) >> Integer.numberOfTrailingZeros(ONE_INBOUND_CHANNEL)),
                    Integer.valueOf((newState & OUTBOUND_CHANNELS_MASK) >> Integer.numberOfTrailingZeros(ONE_OUTBOUND_CHANNEL))
                    );
        }
        return result;
    }

    private void sendCloseRequestBody() {
        final Pooled<ByteBuffer> pooled = remoteConnection.allocate();
        boolean ok = false;
        try {
            final ByteBuffer buffer = pooled.getResource();
            buffer.put(Protocol.CONNECTION_CLOSE);
            buffer.flip();
            remoteConnection.send(pooled, true);
            log.tracef("Sent close request on %s", this);
            ok = true;
        } finally {
            if (! ok) {
                pooled.free();
            }
        }
    }

    public Cancellable open(final String serviceType, final Result<Channel> result, final OptionMap optionMap) {
        log.tracef("Requesting service open of type %s on %s", serviceType, this);
        byte[] serviceTypeBytes = serviceType.getBytes(Protocol.UTF_8);
        final int serviceTypeLength = serviceTypeBytes.length;
        if (serviceTypeLength > 255) {
            log.tracef("Rejecting service open of type %s on %s due to service type name being too long", serviceType, this);
            result.setException(new ServiceOpenException("Service type name is too long"));
            return IoUtils.nullCancellable();
        }

        int id;
        final OptionMap connectionOptionMap = remoteConnection.getOptionMap();

        final int outboundWindowSize = optionMap.get(RemotingOptions.TRANSMIT_WINDOW_SIZE, connectionOptionMap.get(RemotingOptions.TRANSMIT_WINDOW_SIZE, Protocol.DEFAULT_WINDOW_SIZE));
        final int inboundWindowSize = optionMap.get(RemotingOptions.RECEIVE_WINDOW_SIZE, connectionOptionMap.get(RemotingOptions.RECEIVE_WINDOW_SIZE, Protocol.DEFAULT_WINDOW_SIZE));
        final int outboundMessageCount = optionMap.get(RemotingOptions.MAX_OUTBOUND_MESSAGES, connectionOptionMap.get(RemotingOptions.MAX_OUTBOUND_MESSAGES, Protocol.DEFAULT_MESSAGE_COUNT));
        final int inboundMessageCount = optionMap.get(RemotingOptions.MAX_INBOUND_MESSAGES, connectionOptionMap.get(RemotingOptions.MAX_INBOUND_MESSAGES, Protocol.DEFAULT_MESSAGE_COUNT));
        final IntIndexMap<PendingChannel> pendingChannels = this.pendingChannels;
        try {
            handleOutboundChannelOpen();
        } catch (IOException e) {
            result.setException(e);
            return IoUtils.nullCancellable();
        }
        boolean ok = false;
        try {
            final Random random = ProtocolUtils.randomHolder.get();
            for (;;) {
                id = random.nextInt() | 0x80000000;
                if (! pendingChannels.containsKey(id)) {
                    PendingChannel pendingChannel = new PendingChannel(id, outboundWindowSize, inboundWindowSize, outboundMessageCount, inboundMessageCount, result);
                    if (pendingChannels.putIfAbsent(pendingChannel) == null) {
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
                                pendingChannels.removeKey(id);
                                return IoUtils.nullCancellable();
                            }
                            ok = true;
                            log.tracef("Completed initiation of service open of type %s on %s", serviceType, this);
                            // TODO: allow cancel
                            return IoUtils.nullCancellable();
                        } finally {
                            pooled.free();
                        }
                    }
                }
            }
        } finally {
            if (! ok) handleOutboundChannelClosed();
        }
    }

    public Collection<Principal> getPrincipals() {
        return principals;
    }

    public String getRemoteEndpointName() {
        return remoteEndpointName;
    }

    protected void closeAction() throws IOException {
        sendCloseRequest();
    }

    private void closePendingChannels() {
        synchronized (remoteConnection) {
            for (PendingChannel pendingChannel : pendingChannels) {
                pendingChannel.getResult().setCancelled();
            }
        }
    }

    private void closeAllChannels() {
        synchronized (remoteConnection) {
            for (RemoteConnectionChannel channel : channels) {
                channel.closeAsync();
            }
        }
    }

    ConnectionHandlerContext getConnectionContext() {
        return connectionContext;
    }

    RemoteConnectionChannel addChannel(final RemoteConnectionChannel channel) {
        return channels.putIfAbsent(channel);
    }

    RemoteConnectionChannel getChannel(final int id) {
        return channels.get(id);
    }

    PendingChannel removePendingChannel(final int id) {
        return pendingChannels.removeKey(id);
    }

    void putChannel(final RemoteConnectionChannel channel) {
        channels.put(channel);
    }

    public String toString() {
        return String.format("Connection handler for %s", remoteConnection);
    }
}
