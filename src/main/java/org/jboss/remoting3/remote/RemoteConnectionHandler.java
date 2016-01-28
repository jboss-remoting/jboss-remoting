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

import static org.jboss.remoting3.remote.RemoteLogger.log;
import static org.xnio.Bits.anyAreSet;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import javax.net.ssl.SSLSession;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.NotOpenException;
import org.jboss.remoting3.ProtocolException;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.ServiceOpenException;
import org.jboss.remoting3._private.Equaller;
import org.jboss.remoting3._private.IntIndexHashMap;
import org.jboss.remoting3._private.IntIndexMap;
import org.jboss.remoting3.security.UserInfo;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.xnio.Bits;
import org.xnio.Cancellable;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pooled;
import org.xnio.Result;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.SslChannel;

final class RemoteConnectionHandler extends AbstractHandleableCloseable<ConnectionHandler> implements ConnectionHandler {

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
    private final UserInfo userInfo;

    private final int maxInboundChannels;
    private final int maxOutboundChannels;

    private final String remoteEndpointName;

    private final int behavior;

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

    RemoteConnectionHandler(final ConnectionHandlerContext connectionContext, final RemoteConnection remoteConnection, final Collection<Principal> principals, final UserInfo userInfo, final int maxInboundChannels, final int maxOutboundChannels, final String remoteEndpointName, final int behavior) {
        super(remoteConnection.getExecutor());
        this.connectionContext = connectionContext;
        this.remoteConnection = remoteConnection;
        this.maxInboundChannels = maxInboundChannels;
        this.maxOutboundChannels = maxOutboundChannels;
        this.remoteEndpointName = remoteEndpointName;
        this.behavior = behavior;

        this.principals = Collections.unmodifiableCollection(principals);
        this.userInfo = userInfo;
    }

    /**
     * The socket channel was closed with or without our consent.
     */
    void handleConnectionClose() {
        remoteConnection.shutdownWrites();
        closePendingChannels();
        closeAllChannels();
    }

    /**
     * Make this method visible.
     */
    protected void closeComplete() {
        super.closeComplete();
        remoteConnection.getRemoteConnectionProvider().removeConnectionHandler(this);
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
                log.tracef("Refused inbound channel request on %s because too many inbound channels are open", this);
                return false;
            }
            if ((oldState & SENT_CLOSE_REQ) != 0) {
                log.tracef("Refused inbound channel request on %s because close request was sent", this);
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
                log.tracef("Refused outbound channel open on %s because too many outbound channels are open", this);
                throw new ProtocolException("Too many channels open");
            }
            if ((oldState & SENT_CLOSE_REQ) != 0) {
                log.tracef("Refused outbound channel open on %s because close request was sent", this);
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
        sendCloseRequestBody(remoteConnection);
        log.tracef("Sent close request on %s", this);
    }

    static void sendCloseRequestBody(RemoteConnection remoteConnection) {
        final Pooled<ByteBuffer> pooled = remoteConnection.allocate();
        boolean ok = false;
        try {
            final ByteBuffer buffer = pooled.getResource();
            buffer.put(Protocol.CONNECTION_CLOSE);
            buffer.flip();
            remoteConnection.send(pooled, true);
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

        // Request the maximum outbound value if none was specified.
        final int outboundWindowSizeOptionValue = connectionOptionMap.get(RemotingOptions.TRANSMIT_WINDOW_SIZE, RemotingOptions.OUTGOING_CHANNEL_DEFAULT_TRANSMIT_WINDOW_SIZE);
        final int outboundMessageCountOptionValue = connectionOptionMap.get(RemotingOptions.MAX_OUTBOUND_MESSAGES, RemotingOptions.OUTGOING_CHANNEL_DEFAULT_MAX_OUTBOUND_MESSAGES);
        // Restrict the inbound value to defaults if none was specified.
        final int inboundWindowSizeOptionValue = connectionOptionMap.get(RemotingOptions.RECEIVE_WINDOW_SIZE, RemotingOptions.OUTGOING_CHANNEL_DEFAULT_RECEIVE_WINDOW_SIZE);
        final int inboundMessageCountOptionValue = connectionOptionMap.get(RemotingOptions.MAX_INBOUND_MESSAGES, RemotingOptions.DEFAULT_MAX_INBOUND_MESSAGES);
        // Request the maximum message size to defaults if none was specified.
        final long outboundMessageSizeOptionValue = connectionOptionMap.get(RemotingOptions.MAX_OUTBOUND_MESSAGE_SIZE, RemotingOptions.DEFAULT_MAX_OUTBOUND_MESSAGE_SIZE);
        final long inboundMessageSizeOptionValue = connectionOptionMap.get(RemotingOptions.MAX_INBOUND_MESSAGE_SIZE, RemotingOptions.DEFAULT_MAX_INBOUND_MESSAGE_SIZE);

        final int outboundWindowSize = optionMap.get(RemotingOptions.TRANSMIT_WINDOW_SIZE, outboundWindowSizeOptionValue);
        final int outboundMessageCount = optionMap.get(RemotingOptions.MAX_OUTBOUND_MESSAGES, outboundMessageCountOptionValue);
        final int inboundWindowSize = optionMap.get(RemotingOptions.RECEIVE_WINDOW_SIZE, inboundWindowSizeOptionValue);
        final int inboundMessageCount = optionMap.get(RemotingOptions.MAX_INBOUND_MESSAGES, inboundMessageCountOptionValue);
        final long outboundMessageSize = optionMap.get(RemotingOptions.MAX_OUTBOUND_MESSAGE_SIZE, outboundMessageSizeOptionValue);
        final long inboundMessageSize = optionMap.get(RemotingOptions.MAX_INBOUND_MESSAGE_SIZE, inboundMessageSizeOptionValue);

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
                    PendingChannel pendingChannel = new PendingChannel(id, outboundWindowSize, inboundWindowSize, outboundMessageCount, inboundMessageCount, outboundMessageSize, inboundMessageSize, result);
                    if (pendingChannels.putIfAbsent(pendingChannel) == null) {
                        if (log.isTraceEnabled()) {
                            log.tracef("Outbound service request for channel %08x is configured as follows:\n" +
                                    "  outbound window:  option %10d, req %10d\n" +
                                    "  inbound window:   option %10d, req %10d\n" +
                                    "  outbound msgs:    option %10d, req %10d\n" +
                                    "  inbound msgs:     option %10d, req %10d\n" +
                                    "  outbound msgsize: option %19d, req %19d\n" +
                                    "  inbound msgsize:  option %19d, req %19d",
                                Integer.valueOf(id),
                                Integer.valueOf(outboundWindowSizeOptionValue), Integer.valueOf(outboundWindowSize),
                                Integer.valueOf(inboundWindowSizeOptionValue), Integer.valueOf(inboundWindowSize),
                                Integer.valueOf(outboundMessageCountOptionValue), Integer.valueOf(outboundMessageCount),
                                Integer.valueOf(inboundMessageCountOptionValue), Integer.valueOf(inboundMessageCount),
                                Long.valueOf(outboundMessageSizeOptionValue), Long.valueOf(outboundMessageSize),
                                Long.valueOf(inboundMessageSizeOptionValue), Long.valueOf(inboundMessageSize)
                            );
                        }
                        if (anyAreSet(channelState, RECEIVED_CLOSE_REQ | SENT_CLOSE_REQ)) {
                            // there's a chance that the connection was closed after the channel open was registered in the map here
                            pendingChannels.remove(pendingChannel);
                            result.setCancelled();
                            return IoUtils.nullCancellable();
                        }

                        Pooled<ByteBuffer> pooled = remoteConnection.allocate();
                        try {
                            ByteBuffer buffer = pooled.getResource();
                            buffer.put(Protocol.CHANNEL_OPEN_REQUEST);
                            buffer.putInt(id);
                            ProtocolUtils.writeBytes(buffer, Protocol.O_SERVICE_NAME, serviceTypeBytes);
                            ProtocolUtils.writeInt(buffer, Protocol.O_MAX_INBOUND_MSG_WINDOW_SIZE, inboundWindowSize);
                            ProtocolUtils.writeShort(buffer, Protocol.O_MAX_INBOUND_MSG_COUNT, inboundMessageCount);
                            ProtocolUtils.writeInt(buffer, Protocol.O_MAX_OUTBOUND_MSG_WINDOW_SIZE, outboundWindowSize);
                            ProtocolUtils.writeShort(buffer, Protocol.O_MAX_OUTBOUND_MSG_COUNT, outboundMessageCount);
                            if (inboundMessageSize != Long.MAX_VALUE) {
                                ProtocolUtils.writeLong(buffer, Protocol.O_MAX_INBOUND_MSG_SIZE, inboundMessageSize);
                            }
                            if (outboundMessageSize != Long.MAX_VALUE) {
                                ProtocolUtils.writeLong(buffer, Protocol.O_MAX_OUTBOUND_MSG_SIZE, outboundMessageSize);
                            }
                            buffer.put((byte) 0);
                            buffer.flip();
                            remoteConnection.send(pooled);
                            ok = true;
                            log.tracef("Completed initiation of service open of type %s on %s", serviceType, this);
                            // TODO: allow cancel
                            return IoUtils.nullCancellable();
                        } finally {
                            if (! ok) pooled.free();
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

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public SSLSession getSslSession() {
        SslChannel sslChannel = remoteConnection.getSslChannel();
        return sslChannel != null ? sslChannel.getSslSession() : null;
    }

    public String getRemoteEndpointName() {
        return remoteEndpointName;
    }

    protected void closeAction() throws IOException {
        sendCloseRequest();
        remoteConnection.shutdownWrites();
        // now these guys can't send useless messages
        closePendingChannels();
        closeAllChannels();
        remoteConnection.getRemoteConnectionProvider().removeConnectionHandler(this);
    }

    private void closePendingChannels() {
        final ArrayList<PendingChannel> list;
        synchronized (remoteConnection.getLock()) {
            list = new ArrayList<PendingChannel>(pendingChannels);
        }
        for (PendingChannel pendingChannel : list) {
            pendingChannel.getResult().setCancelled();
        }
    }

    private void closeAllChannels() {
        final ArrayList<RemoteConnectionChannel> list;
        synchronized (remoteConnection.getLock()) {
            list = new ArrayList<RemoteConnectionChannel>(channels);
        }
        for (RemoteConnectionChannel channel : list) {
            channel.closeAsync();
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

    boolean isMessageClose() {
        return Bits.allAreSet(behavior, Protocol.BH_MESSAGE_CLOSE);
    }

    boolean isFaultyMessageSize() {
        return Bits.allAreSet(behavior, Protocol.BH_FAULTY_MSG_SIZE);
    }

    public String toString() {
        return String.format("Connection handler for %s", remoteConnection);
    }

    void dumpState(final StringBuilder b) {
        synchronized (remoteConnection.getLock()) {
            final int state = this.channelState;
            final boolean sentCloseReq = Bits.allAreSet(state, SENT_CLOSE_REQ);
            final boolean receivedCloseReq = Bits.allAreSet(state, RECEIVED_CLOSE_REQ);
            final int inboundChannels = (state & INBOUND_CHANNELS_MASK) >>> Integer.numberOfTrailingZeros(ONE_INBOUND_CHANNEL);
            final int outboundChannels = (state & OUTBOUND_CHANNELS_MASK) >>> Integer.numberOfTrailingZeros(ONE_OUTBOUND_CHANNEL);
            final ConnectedMessageChannel channel = remoteConnection.getChannel();
            final SocketAddress localAddress = channel.getLocalAddress();
            final SocketAddress peerAddress = channel.getPeerAddress();
            b.append("    ").append("Connection ").append(localAddress).append(" <-> ").append(peerAddress).append('\n');
            b.append("    ").append("Channel: ").append(channel).append('\n');
            b.append("    ").append("* Flags: ");
            if (Bits.allAreSet(behavior, Protocol.BH_MESSAGE_CLOSE)) b.append("supports-message-close ");
            if (Bits.allAreSet(behavior, Protocol.BH_FAULTY_MSG_SIZE)) b.append("remote-faulty-message-size ");
            if (receivedCloseReq) b.append("received-close-req ");
            if (sentCloseReq) b.append("set-close-req ");
            b.append('\n');
            b.append("    ").append("* ").append(inboundChannels).append(" (max ").append(maxInboundChannels).append(") inbound channels\n");
            b.append("    ").append("* ").append(outboundChannels).append(" (max ").append(maxOutboundChannels).append(") outbound channels\n");
            b.append("    ").append("* Channels:\n");
            for (RemoteConnectionChannel connectionChannel : channels) {
                connectionChannel.dumpState(b);
            }
        }
    }
}
