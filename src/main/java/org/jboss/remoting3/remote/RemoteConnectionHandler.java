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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.NotOpenException;
import org.jboss.remoting3.ProtocolException;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.ServiceOpenException;
import org.jboss.remoting3.security.InetAddressPrincipal;
import org.jboss.remoting3.security.UserPrincipal;
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
import org.xnio.channels.SslChannel;

import javax.net.ssl.SSLPeerUnverifiedException;

final class RemoteConnectionHandler extends AbstractHandleableCloseable<ConnectionHandler> implements ConnectionHandler {

    static final int LENGTH_PLACEHOLDER = 0;

    private final ConnectionHandlerContext connectionContext;
    private final RemoteConnection remoteConnection;
    private final Object channelSemaphoreLock = new Object();

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

    // todo limit or whatever
    private int channelCount = 50;

    private volatile int channelState = 0;

    private static final AtomicIntegerFieldUpdater<RemoteConnectionHandler> channelStateUpdater = AtomicIntegerFieldUpdater.newUpdater(RemoteConnectionHandler.class, "channelState");

    private static final int COUNT_MASK         = (1 << 20) - 1; // 20 bits of counter, 12 bits of flags
    private static final int FLAGS_MASK         = ~ COUNT_MASK;

    /** Sending close request, now shutting down the write side of all channels and refusing new channels. Once send, received = true and count == 0, shut down writes on the socket. */
    private static final int SENT_CLOSE_REQ   = (1 << 20);
    /** Received close request.  Send a close req if we haven't already done so. */
    private static final int RECEIVED_CLOSE_REQ = (1 << 21);

    RemoteConnectionHandler(final ConnectionHandlerContext connectionContext, final RemoteConnection remoteConnection, final String authorizationId) {
        super(remoteConnection.getExecutor());
        this.connectionContext = connectionContext;
        this.remoteConnection = remoteConnection;
        final SslChannel sslChannel = remoteConnection.getSslChannel();
        final Set<Principal> principals = new LinkedHashSet<Principal>();
        if (sslChannel != null) {
            try {
                final Principal peerPrincipal = sslChannel.getSslSession().getPeerPrincipal();
                principals.add(peerPrincipal);
            } catch (SSLPeerUnverifiedException ignored) {
            }
        }
        if (authorizationId != null) {
            principals.add(new UserPrincipal(authorizationId));
        }
        final ConnectedMessageChannel channel = remoteConnection.getChannel();
        final InetSocketAddress address = channel.getPeerAddress(InetSocketAddress.class);
        if (address != null) {
            principals.add(new InetAddressPrincipal(address.getAddress()));
        }
        this.principals = Collections.unmodifiableSet(principals);
    }

    RemoteConnectionHandler(final ConnectionHandlerContext connectionContext, final RemoteConnection remoteConnection) {
        this(connectionContext, remoteConnection, null);
    }

    /**
     * The channel was closed with or without our consent.
     */
    void handleChannelClose() {
        closePendingChannels();
        closeAllChannels();
    }

    /**
     * A channel was closed, locally or remotely.
     */
    void decrementChannelCount() {
        // use decrement because the channel count is in the low-order bits.
        int oldState = channelStateUpdater.getAndDecrement(this);
        if ((oldState & COUNT_MASK) == COUNT_MASK) {
            // some kind of problem, we decremented below zero, just try to recover
            channelStateUpdater.incrementAndGet(this);
            throw new Error("Decremented channel count below zero");
        }
        if ((oldState & COUNT_MASK) == 0 && (oldState & (SENT_CLOSE_REQ | RECEIVED_CLOSE_REQ)) == (SENT_CLOSE_REQ | RECEIVED_CLOSE_REQ)) {
            remoteConnection.shutdownWrites();
        }
    }

    /**
     * A channel was opened, locally or remotely.
     *
     * @throws IOException if there are too many channels open
     */
    void incrementChannelCount() throws IOException {
        int oldState, newState;
        do {
            oldState = channelState;
            int oldCount = oldState & COUNT_MASK;
            if (oldCount == COUNT_MASK) {
                throw new ProtocolException("Too many channels open");
            }
            if ((oldState & SENT_CLOSE_REQ) != 0) {
                throw new NotOpenException("Cannot open new channel because close was initiated");
            }
            newState = oldState & FLAGS_MASK | oldCount + 1;
        } while (! channelStateUpdater.compareAndSet(this, oldState, newState));
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
        } while (! channelStateUpdater.compareAndSet(this, oldState, newState));
        closePendingChannels();
        if ((oldState & SENT_CLOSE_REQ) == 0) {
            sendCloseRequestBody();
            closeAllChannels();
        }
        if ((oldState & COUNT_MASK) == 0) {
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
        } while (! channelStateUpdater.compareAndSet(this, oldState, newState));
        sendCloseRequestBody();
        closeAllChannels();
        if ((oldState & COUNT_MASK) == 0) {
            remoteConnection.shutdownWrites();
        }
    }

    private void sendCloseRequestBody() {
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
        final IntIndexMap<PendingChannel> pendingChannels = this.pendingChannels;
        int channelCount;
        try {
            incrementChannelCount();
        } catch (IOException e) {
            result.setException(e);
            return IoUtils.nullCancellable();
        }
        boolean ok = false;
        try {
            synchronized (channelSemaphoreLock) {
                while ((channelCount = this.channelCount) == 0) {
                    try {
                        channelSemaphoreLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        result.setException(new InterruptedIOException("Interrupted while waiting to write message"));
                        return IoUtils.nullCancellable();
                    }
                    this.channelCount = channelCount - 1;
                }
            }
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
                                // TODO: allow cancel
                                return IoUtils.nullCancellable();
                            } finally {
                                pooled.free();
                            }
                        }
                    }
                }
            } finally {
                if (! ok) synchronized (channelSemaphoreLock) {
                    this.channelCount++;
                    channelSemaphoreLock.notify();
                }
            }
        } finally {
            if (! ok) decrementChannelCount();
        }
    }

    public Collection<Principal> getPrincipals() {
        return principals;
    }
    
    protected void proceedClose() throws IOException {
        synchronized(this) {
            if (isClosing()) {
                // it is a case of async close that is waiting for a signal to proceed
                closeAction();
            }
        }
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
            channel.getRemoteConnection().handleException(new IOException("Attempted to add an already-existing channel"));
        }
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
}
