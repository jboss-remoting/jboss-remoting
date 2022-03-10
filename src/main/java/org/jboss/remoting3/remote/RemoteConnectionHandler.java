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

import static org.jboss.remoting3._private.Messages.log;
import static org.xnio.Bits.anyAreSet;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.xnio.Bits;
import org.xnio.Cancellable;
import org.xnio.Connection;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pooled;
import org.xnio.Result;
import org.xnio.channels.SslChannel;

@SuppressWarnings("deprecation")
final class RemoteConnectionHandler extends AbstractHandleableCloseable<ConnectionHandler> implements ConnectionHandler {

    // TODO JBEAP-20756 temporarily using a system property as a solution to ACK TIMEOUT issue until an RFE is properly submitted
    private static final long MESSAGE_ACK_TIMEOUT = Long.parseLong(WildFlySecurityManager.getPropertyPrivileged("org.jboss.remoting3.remote.message.ack.timeout", String.valueOf(RemotingOptions.DEFAULT_MESSAGE_ACK_TIMEOUT))) * 1_000_000;

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

    private final int maxInboundChannels;
    private final int maxOutboundChannels;

    private final String remoteEndpointName;

    private final int behavior;
    private final boolean supportsRemoteAuth;
    private final Set<String> offeredMechanisms;

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
    private final Principal principal;
    private final String peerSaslServerName;
    private final String localSaslServerName;

    RemoteConnectionHandler(final ConnectionHandlerContext connectionContext, final RemoteConnection remoteConnection, final int maxInboundChannels, final int maxOutboundChannels, final Principal principal, final String remoteEndpointName, final int behavior, final boolean supportsRemoteAuth, final Set<String> offeredMechanisms, final String peerSaslServerName, final String localSaslServerName) {
        super(remoteConnection.getExecutor());
        this.connectionContext = connectionContext;
        this.remoteConnection = remoteConnection;
        this.maxInboundChannels = maxInboundChannels;
        this.maxOutboundChannels = maxOutboundChannels;
        this.principal = principal;
        this.remoteEndpointName = remoteEndpointName;
        this.behavior = behavior;
        this.supportsRemoteAuth = supportsRemoteAuth;
        this.offeredMechanisms = offeredMechanisms;
        this.peerSaslServerName = peerSaslServerName;
        this.localSaslServerName = localSaslServerName;
    }

    /**
     * The socket channel was closed with or without our consent.
     */
    void handleConnectionClose() {
        receiveCloseRequest();
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
        byte[] serviceTypeBytes = serviceType.getBytes(StandardCharsets.UTF_8);
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
            final Random random = ThreadLocalRandom.current();
            for (;;) {
                id = random.nextInt() | 0x80000000;
                if (! pendingChannels.containsKey(id)) {
                    PendingChannel pendingChannel = new PendingChannel(id, outboundWindowSize, inboundWindowSize, outboundMessageCount, inboundMessageCount, outboundMessageSize, inboundMessageSize, MESSAGE_ACK_TIMEOUT, result);
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

    public void sendAuthRequest(final int id, final String mechName, final byte[] initialResponse) throws IOException {
        log.tracef("Sending authentication request for ID %08x, mech %s", id, mechName);
        final byte[] mechNameBytes = mechName.getBytes(StandardCharsets.UTF_8);
        final int length = mechNameBytes.length;
        if (length == 0 || length > 256) {
            throw log.mechanismNameTooLong(mechName);
        }
        int requiredSize = 6 + length + ((initialResponse != null) ? initialResponse.length : 0);
        final Pooled<ByteBuffer> pooled = remoteConnection.allocate();
        boolean ok = false;
        try {
            final ByteBuffer buffer = pooled.getResource();
            if (buffer.remaining() < requiredSize) {
                throw log.authenticationMessageTooLarge();
            }
            buffer.put(Protocol.APP_AUTH_REQUEST);
            buffer.putInt(id);
            buffer.put((byte) length);
            buffer.put(mechNameBytes);
            if (initialResponse != null) {
                buffer.put(initialResponse);
            }
            buffer.flip();
            remoteConnection.send(pooled);
            ok = true;
        } finally {
            if (! ok) pooled.free();
        }
    }

    public void sendAuthChallenge(final int id, final byte[] challenge) throws IOException {
        log.tracef("Sending authentication challenge for ID %08x", id);
        int requiredSize = 5 + challenge.length;
        final Pooled<ByteBuffer> pooled = remoteConnection.allocate();
        boolean ok = false;
        try {
            final ByteBuffer buffer = pooled.getResource();
            if (buffer.remaining() < requiredSize) {
                throw log.authenticationMessageTooLarge();
            }
            buffer.put(Protocol.APP_AUTH_CHALLENGE);
            buffer.putInt(id);
            buffer.put(challenge);
            buffer.flip();
            remoteConnection.send(pooled);
            ok = true;
        } finally {
            if (! ok) pooled.free();
        }
    }

    public void sendAuthResponse(final int id, final byte[] response) throws IOException {
        log.tracef("Sending authentication response for ID %08x", id);
        int requiredSize = 5 + response.length;
        final Pooled<ByteBuffer> pooled = remoteConnection.allocate();
        boolean ok = false;
        try {
            final ByteBuffer buffer = pooled.getResource();
            if (buffer.remaining() < requiredSize) {
                throw log.authenticationMessageTooLarge();
            }
            buffer.put(Protocol.APP_AUTH_RESPONSE);
            buffer.putInt(id);
            buffer.put(response);
            buffer.flip();
            remoteConnection.send(pooled);
            ok = true;
        } finally {
            if (! ok) pooled.free();
        }
    }

    public void sendAuthSuccess(final int id, final byte[] challenge) throws IOException {
        log.tracef("Sending authentication success for ID %08x", id);
        int requiredSize = 5 + ((challenge != null) ? challenge.length : 0);
        final Pooled<ByteBuffer> pooled = remoteConnection.allocate();
        boolean ok = false;
        try {
            final ByteBuffer buffer = pooled.getResource();
            if (buffer.remaining() < requiredSize) {
                throw log.authenticationMessageTooLarge();
            }
            buffer.put(Protocol.APP_AUTH_SUCCESS);
            buffer.putInt(id);
            if (challenge != null) {
                buffer.put(challenge);
            }
            buffer.flip();
            remoteConnection.send(pooled);
            ok = true;
        } finally {
            if (! ok) pooled.free();
        }
    }

    public void sendAuthReject(final int id) throws IOException {
        log.tracef("Sending authentication reject for ID %08x", id);
        // todo: allocate small buffer
        final Pooled<ByteBuffer> pooled = remoteConnection.allocate();
        boolean ok = false;
        try {
            final ByteBuffer buffer = pooled.getResource();
            buffer.put(Protocol.APP_AUTH_REJECT);
            buffer.putInt(id);
            buffer.flip();
            remoteConnection.send(pooled);
            ok = true;
        } finally {
            if (! ok) pooled.free();
        }
    }

    public void sendAuthDelete(final int id) throws IOException {
        log.tracef("Sending authentication delete for ID %08x", id);
        // todo: allocate small buffer
        final Pooled<ByteBuffer> pooled = remoteConnection.allocate();
        boolean ok = false;
        try {
            final ByteBuffer buffer = pooled.getResource();
            buffer.put(Protocol.APP_AUTH_DELETE);
            buffer.putInt(id);
            buffer.flip();
            remoteConnection.send(pooled);
            ok = true;
        } finally {
            if (! ok) pooled.free();
        }
    }

    public void sendAuthDeleteAck(final int id) throws IOException {
        log.tracef("Sending authentication delete ack for ID %08x", id);
        // todo: allocate small buffer
        final Pooled<ByteBuffer> pooled = remoteConnection.allocate();
        boolean ok = false;
        try {
            final ByteBuffer buffer = pooled.getResource();
            buffer.put(Protocol.APP_AUTH_DELETE_ACK);
            buffer.putInt(id);
            buffer.flip();
            remoteConnection.send(pooled);
            ok = true;
        } finally {
            if (! ok) pooled.free();
        }
    }

    public SSLSession getSslSession() {
        SslChannel sslChannel = remoteConnection.getSslChannel();
        return sslChannel != null ? sslChannel.getSslSession() : null;
    }

    public String getRemoteEndpointName() {
        return remoteEndpointName;
    }

    public SocketAddress getLocalAddress() {
        return remoteConnection.getLocalAddress();
    }

    public SocketAddress getPeerAddress() {
        return remoteConnection.getPeerAddress();
    }

    public String getPeerSaslServerName() {
        return peerSaslServerName;
    }

    public String getLocalSaslServerName() {
        return localSaslServerName;
    }

    public SecurityIdentity getLocalIdentity() {
        return remoteConnection.getIdentity();
    }

    public boolean supportsRemoteAuth() {
        return supportsRemoteAuth;
    }

    public Set<String> getOfferedMechanisms() {
        return offeredMechanisms;
    }

    public Principal getPrincipal() {
        return principal;
    }

    protected void closeAction() throws IOException {
        sendCloseRequest();
        remoteConnection.shutdownWrites();
        remoteConnection.getMessageReader().shutdownReads();
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
            final Connection connection = remoteConnection.getConnection();
            final SocketAddress localAddress = connection.getLocalAddress();
            final SocketAddress peerAddress = connection.getPeerAddress();
            b.append("    ").append("Connection ").append(localAddress).append(" <-> ").append(peerAddress).append('\n');
            b.append("    ").append("Raw: ").append(connection).append('\n');
            b.append("    ").append("* Flags: ");
            if (Bits.allAreSet(behavior, Protocol.BH_MESSAGE_CLOSE)) b.append("supports-message-close ");
            if (Bits.allAreSet(behavior, Protocol.BH_FAULTY_MSG_SIZE)) b.append("remote-faulty-message-size ");
            if (supportsRemoteAuth) b.append("auth-cap ");
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
