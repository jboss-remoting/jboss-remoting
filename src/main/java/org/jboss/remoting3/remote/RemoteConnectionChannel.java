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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.jboss.remoting3.Attachments;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.ChannelBusyException;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.MessageCancelledException;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.NotOpenException;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.xnio.Bits;
import org.xnio.Option;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedMessageChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteConnectionChannel extends AbstractHandleableCloseable<Channel> implements Channel {

    static final IntIndexer<RemoteConnectionChannel> INDEXER = new IntIndexer<RemoteConnectionChannel>() {
        public int getKey(final RemoteConnectionChannel argument) {
            return argument.channelId;
        }

        public boolean equals(final RemoteConnectionChannel argument, final int index) {
            return argument.channelId == index;
        }
    };

    private final RemoteConnectionHandler connectionHandler;
    private final ConnectionHandlerContext connectionHandlerContext;
    private final RemoteConnection connection;
    private final int channelId;
    private final IntIndexMap<OutboundMessage> outboundMessages = new IntIndexHashMap<OutboundMessage>(OutboundMessage.INDEXER, Equaller.IDENTITY, 512, 0.5f);
    private final IntIndexMap<InboundMessage> inboundMessages = new IntIndexHashMap<InboundMessage>(InboundMessage.INDEXER, Equaller.IDENTITY, 512, 0.5f);
    private final int outboundWindow;
    private final int inboundWindow;
    private final Attachments attachments = new Attachments();
    private final Queue<InboundMessage> inboundMessageQueue = new ArrayDeque<InboundMessage>();
    private final int maxOutboundMessages;
    private final int maxInboundMessages;
    private final long maxOutboundMessageSize;
    private final long maxInboundMessageSize;
    private volatile int channelState = 0;

    private static final AtomicIntegerFieldUpdater<RemoteConnectionChannel> channelStateUpdater = AtomicIntegerFieldUpdater.newUpdater(RemoteConnectionChannel.class, "channelState");

    private Receiver nextReceiver;

    private static final int WRITE_CLOSED = (1 << 31);
    private static final int READ_CLOSED = (1 << 30);
    private static final int OUTBOUND_MESSAGES_MASK = (1 << 15) - 1;
    private static final int ONE_OUTBOUND_MESSAGE = 1;
    private static final int INBOUND_MESSAGES_MASK = ((1 << 30) - 1) & ~OUTBOUND_MESSAGES_MASK;
    private static final int ONE_INBOUND_MESSAGE = (1 << 15);

    RemoteConnectionChannel(final RemoteConnectionHandler connectionHandler, final RemoteConnection connection, final int channelId, final int outboundWindow, final int inboundWindow, final int maxOutboundMessages, final int maxInboundMessages, final long maxOutboundMessageSize, final long maxInboundMessageSize) {
        super(connectionHandler.getConnectionContext().getConnectionProviderContext().getExecutor(), true);
        this.maxOutboundMessageSize = maxOutboundMessageSize;
        this.maxInboundMessageSize = maxInboundMessageSize;
        connectionHandlerContext = connectionHandler.getConnectionContext();
        this.connectionHandler = connectionHandler;
        this.connection = connection;
        this.channelId = channelId;
        this.outboundWindow = outboundWindow;
        this.inboundWindow = inboundWindow;
        this.maxOutboundMessages = maxOutboundMessages;
        this.maxInboundMessages = maxInboundMessages;
    }

    void openOutboundMessage() throws IOException {
        int oldState, newState;
        do {
            oldState = channelState;
            if ((oldState & WRITE_CLOSED) != 0) {
                throw new NotOpenException("Writes closed");
            }
            final int outboundCount = oldState & OUTBOUND_MESSAGES_MASK;
            if (outboundCount == maxOutboundMessages) {
                throw new ChannelBusyException("Too many open outbound writes");
            }
            newState = oldState + ONE_OUTBOUND_MESSAGE;
        } while (!casState(oldState, newState));
        log.tracef("Opened outbound message on %s", this);
    }

    private int incrementState(final int count) {
        final int oldState = channelStateUpdater.getAndAdd(this, count);
        if (log.isTraceEnabled()) {
            final int newState = oldState + count;
            log.tracef("CAS %s\n\told: RS=%s WS=%s IM=%d OM=%d\n\tnew: RS=%s WS=%s IM=%d OM=%d", this,
                    Boolean.valueOf((oldState & READ_CLOSED) != 0),
                    Boolean.valueOf((oldState & WRITE_CLOSED) != 0),
                    Integer.valueOf((oldState & INBOUND_MESSAGES_MASK) >> Integer.numberOfTrailingZeros(ONE_INBOUND_MESSAGE)),
                    Integer.valueOf((oldState & OUTBOUND_MESSAGES_MASK) >> Integer.numberOfTrailingZeros(ONE_OUTBOUND_MESSAGE)),
                    Boolean.valueOf((newState & READ_CLOSED) != 0),
                    Boolean.valueOf((newState & WRITE_CLOSED) != 0),
                    Integer.valueOf((newState & INBOUND_MESSAGES_MASK) >> Integer.numberOfTrailingZeros(ONE_INBOUND_MESSAGE)),
                    Integer.valueOf((newState & OUTBOUND_MESSAGES_MASK) >> Integer.numberOfTrailingZeros(ONE_OUTBOUND_MESSAGE))
                    );
        }
        return oldState;
    }

    private boolean casState(final int oldState, final int newState) {
        final boolean result = channelStateUpdater.compareAndSet(this, oldState, newState);
        if (result && log.isTraceEnabled()) {
            log.tracef("CAS %s\n\told: RS=%s WS=%s IM=%d OM=%d\n\tnew: RS=%s WS=%s IM=%d OM=%d", this,
                    Boolean.valueOf((oldState & READ_CLOSED) != 0),
                    Boolean.valueOf((oldState & WRITE_CLOSED) != 0),
                    Integer.valueOf((oldState & INBOUND_MESSAGES_MASK) >> Integer.numberOfTrailingZeros(ONE_INBOUND_MESSAGE)),
                    Integer.valueOf((oldState & OUTBOUND_MESSAGES_MASK) >> Integer.numberOfTrailingZeros(ONE_OUTBOUND_MESSAGE)),
                    Boolean.valueOf((newState & READ_CLOSED) != 0),
                    Boolean.valueOf((newState & WRITE_CLOSED) != 0),
                    Integer.valueOf((newState & INBOUND_MESSAGES_MASK) >> Integer.numberOfTrailingZeros(ONE_INBOUND_MESSAGE)),
                    Integer.valueOf((newState & OUTBOUND_MESSAGES_MASK) >> Integer.numberOfTrailingZeros(ONE_OUTBOUND_MESSAGE))
                    );
        }
        return result;
    }

    void closeOutboundMessage() {
        int oldState = incrementState(-ONE_OUTBOUND_MESSAGE);
        if (oldState == (WRITE_CLOSED | READ_CLOSED)) {
            // no messages left and read & write closed
            log.tracef("Closed outbound message on %s (unregistering)", this);
            unregister();
        } else {
            log.tracef("Closed outbound message on %s", this);
        }
    }

    boolean openInboundMessage() {
        int oldState, newState;
        do {
            oldState = channelState;
            if ((oldState & READ_CLOSED) != 0) {
                log.tracef("Refusing inbound message on %s (reads closed)", this);
                return false;
            }
            final int inboundCount = oldState & INBOUND_MESSAGES_MASK;
            if (inboundCount == maxInboundMessages) {
                log.tracef("Refusing inbound message on %s (too many concurrent reads)", this);
                return false;
            }
            newState = oldState + ONE_INBOUND_MESSAGE;
        } while (!casState(oldState, newState));
        log.tracef("Opened inbound message on %s", this);
        return true;
    }

    void closeInboundMessage() {
        int oldState = incrementState(-ONE_INBOUND_MESSAGE);
        if (oldState == (WRITE_CLOSED | READ_CLOSED)) {
            // no messages left and read & write closed
            log.tracef("Closed inbound message on %s (unregistering)", this);
            unregister();
        } else {
            log.tracef("Closed inbound message on %s", this);
        }
    }

    void closeReads() {
        int oldState, newState;
        do {
            oldState = channelState;
            if ((oldState & READ_CLOSED) != 0) {
                return;
            }
            newState = oldState | READ_CLOSED;
        } while (!casState(oldState, newState));
        if (oldState == WRITE_CLOSED) {
            // no channels
            log.tracef("Closed channel reads on %s (unregistering)", this);
            unregister();
            closeComplete();
        } else {
            log.tracef("Closed channel reads on %s", this);
        }
        notifyEnd();
    }

    boolean closeWrites() {
        int oldState, newState;
        do {
            oldState = channelState;
            if ((oldState & WRITE_CLOSED) != 0) {
                return false;
            }
            newState = oldState | WRITE_CLOSED;
        } while (!casState(oldState, newState));
        if (oldState == READ_CLOSED) {
            // no channels and read was closed
            log.tracef("Closed channel writes on %s (unregistering)", this);
            unregister();
            closeComplete();
        } else {
            log.tracef("Closed channel writes on %s", this);
        }
        return true;
    }

    boolean closeReadsAndWrites() {
        int oldState, newState;
        do {
            oldState = channelState;
            if ((oldState & (READ_CLOSED | WRITE_CLOSED)) == (READ_CLOSED | WRITE_CLOSED)) {
                return false;
            }
            newState = oldState | READ_CLOSED | WRITE_CLOSED;
        } while (!casState(oldState, newState));
        if ((oldState & WRITE_CLOSED) == 0) {
            // we're sending the write close request asynchronously
            Pooled<ByteBuffer> pooled = connection.allocate();
            boolean ok = false;
            try {
                ByteBuffer byteBuffer = pooled.getResource();
                byteBuffer.put(Protocol.CHANNEL_SHUTDOWN_WRITE);
                byteBuffer.putInt(channelId);
                byteBuffer.flip();
                ok = true;
                connection.send(pooled);
            } finally {
                if (! ok) pooled.free();
            }
            log.tracef("Closed channel reads on %s", this);
        }
        if ((oldState & (INBOUND_MESSAGES_MASK | OUTBOUND_MESSAGES_MASK)) == 0) {
            // there were no channels open
            log.tracef("Closed channel reads and writes on %s (unregistering)", this);
            unregister();
        } else {
            log.tracef("Closed channel reads and writes on %s", this);
        }
        closeComplete();
        notifyEnd();
        return true;
    }

    private void notifyEnd() {
        synchronized (connection.getLock()) {
            if (nextReceiver != null) {
                final Receiver receiver = nextReceiver;
                nextReceiver = null;
                try {
                    getExecutor().execute(new Runnable() {
                        public void run() {
                            receiver.handleEnd(RemoteConnectionChannel.this);
                        }
                    });
                } catch (Throwable t) {
                    connection.handleException(new IOException("Fatal connection error", t));
                    return;
                }
            }
        }
    }

    private void unregister() {
        log.tracef("Unregistering %s", this);
        closeAsync();
        connectionHandler.handleChannelClosed(this);
    }

    public MessageOutputStream writeMessage() throws IOException {
        int tries = 50;
        IntIndexMap<OutboundMessage> outboundMessages = this.outboundMessages;
        openOutboundMessage();
        boolean ok = false;
        try {
            final Random random = ProtocolUtils.randomHolder.get();
            while (tries > 0) {
                final int id = random.nextInt() & 0xfffe;
                if (! outboundMessages.containsKey(id)) {
                    OutboundMessage message = new OutboundMessage((short) id, this, outboundWindow, maxOutboundMessageSize);
                    OutboundMessage existing = outboundMessages.putIfAbsent(message);
                    if (existing == null) {
                        ok = true;
                        return message;
                    }
                }
                tries --;
            }
            throw log.channelBusy();
        } finally {
            if (! ok) {
                closeOutboundMessage();
            }
        }
    }

    void free(OutboundMessage outboundMessage) {
        if (outboundMessages.remove(outboundMessage)) {
            log.tracef("Removed %s", outboundMessage);
        } else {
            log.tracef("Got redundant free for %s", outboundMessage);
        }
    }

    public void writeShutdown() throws IOException {
        if (closeWrites()) {
            Pooled<ByteBuffer> pooled = connection.allocate();
            boolean ok = false;
            try {
                ByteBuffer byteBuffer = pooled.getResource();
                byteBuffer.put(Protocol.CHANNEL_SHUTDOWN_WRITE);
                byteBuffer.putInt(channelId);
                byteBuffer.flip();
                connection.send(pooled);
                ok = true;
            } finally {
                if (! ok) pooled.free();
            }
        }
    }

    void handleRemoteClose() {
        closeReadsAndWrites();
    }

    void handleIncomingWriteShutdown() {
        closeReads();
    }

    public void receiveMessage(final Receiver handler) {
        synchronized (connection.getLock()) {
            if (inboundMessageQueue.isEmpty()) {
                if ((channelState & READ_CLOSED) != 0) {
                    getExecutor().execute(new Runnable() {
                        public void run() {
                            handler.handleEnd(RemoteConnectionChannel.this);
                        }
                    });
                } else if (nextReceiver != null) {
                    throw new IllegalStateException("Message handler already queued");
                } else {
                    nextReceiver = handler;
                }
            } else {
                final InboundMessage message = inboundMessageQueue.remove();
                try {
                    getExecutor().execute(new Runnable() {
                        public void run() {
                            handler.handleMessage(RemoteConnectionChannel.this, message.messageInputStream);
                        }
                    });
                } catch (Throwable t) {
                    connection.handleException(new IOException("Fatal connection error", t));
                    return;
                }
            }
            connection.getLock().notify();
        }
    }

    private static Set<Option<?>> SUPPORTED_OPTIONS = Option.setBuilder()
            .add(RemotingOptions.MAX_INBOUND_MESSAGES)
            .add(RemotingOptions.MAX_OUTBOUND_MESSAGES)
            .add(RemotingOptions.TRANSMIT_WINDOW_SIZE)
            .add(RemotingOptions.RECEIVE_WINDOW_SIZE)
            .add(RemotingOptions.MAX_INBOUND_MESSAGE_SIZE)
            .add(RemotingOptions.MAX_OUTBOUND_MESSAGE_SIZE)
            .create();

    public boolean supportsOption(final Option<?> option) {
        return SUPPORTED_OPTIONS.contains(option);
    }

    public <T> T getOption(final Option<T> option) {
        if (option == RemotingOptions.MAX_INBOUND_MESSAGES) {
            return option.cast(maxInboundMessages);
        } else if (option == RemotingOptions.MAX_OUTBOUND_MESSAGES) {
            return option.cast(maxOutboundMessages);
        } else if (option == RemotingOptions.RECEIVE_WINDOW_SIZE) {
            return option.cast(inboundWindow);
        } else if (option == RemotingOptions.TRANSMIT_WINDOW_SIZE) {
            return option.cast(outboundWindow);
        } else if (option == RemotingOptions.MAX_INBOUND_MESSAGE_SIZE) {
            return option.cast(maxInboundMessageSize);
        } else if (option == RemotingOptions.MAX_OUTBOUND_MESSAGE_SIZE) {
            return option.cast(maxOutboundMessageSize);
        } else {
            return null;
        }
    }

    public <T> T setOption(final Option<T> option, final T value) throws IllegalArgumentException {
        return null;
    }

    void handleMessageData(final Pooled<ByteBuffer> message) {
        boolean ok1 = false;
        try {
            ByteBuffer buffer = message.getResource();
            int id = buffer.getShort() & 0xffff;
            int flags = buffer.get() & 0xff;
            final InboundMessage inboundMessage;
            if ((flags & Protocol.MSG_FLAG_NEW) != 0) {
                if (! openInboundMessage()) {
                    asyncCloseMessage(id);
                    return;
                }
                boolean ok2 = false;
                try {
                    inboundMessage = new InboundMessage((short) id, this, inboundWindow, maxInboundMessageSize);
                    final InboundMessage existing = inboundMessages.putIfAbsent(inboundMessage);
                    if (existing != null) {
                        existing.handleDuplicate();
                    }
                    synchronized(connection.getLock()) {
                        if (nextReceiver != null) {
                            final Receiver receiver = nextReceiver;
                            nextReceiver = null;
                            try {
                                getExecutor().execute(new Runnable() {
                                    public void run() {
                                        receiver.handleMessage(RemoteConnectionChannel.this, inboundMessage.messageInputStream);
                                    }
                                });
                                ok2 = true;
                            } catch (Throwable t) {
                                connection.handleException(new IOException("Fatal connection error", t));
                                return;
                            }
                        } else {
                            inboundMessageQueue.add(inboundMessage);
                            ok2 = true;
                        }
                    }
                } finally {
                    if (! ok2) freeInboundMessage((short) id);
                }
            } else {
                inboundMessage = inboundMessages.get(id);
                if (inboundMessage == null) {
                    log.tracef("Ignoring message on channel %s for unknown message ID %04x", this, Integer.valueOf(id));
                    return;
                }
            }
            inboundMessage.handleIncoming(message);
            ok1 = true;
        } finally {
            if (! ok1) message.free();
        }
    }

    private void asyncCloseMessage(final int id) {
        Pooled<ByteBuffer> pooled = connection.allocate();
        boolean ok = false;
        try {
            ByteBuffer byteBuffer = pooled.getResource();
            byteBuffer.put(Protocol.MESSAGE_CLOSE);
            byteBuffer.putInt(channelId);
            byteBuffer.putShort((short) id);
            byteBuffer.flip();
            ok = true;
            connection.send(pooled);
        } finally {
            if (! ok) pooled.free();
        }
    }

    void handleWindowOpen(final Pooled<ByteBuffer> pooled) {
        ByteBuffer buffer = pooled.getResource();
        int id = buffer.getShort() & 0xffff;
        final OutboundMessage outboundMessage = outboundMessages.get(id);
        if (outboundMessage == null) {
            // ignore; probably harmless...?
            return;
        }
        outboundMessage.acknowledge(buffer.getInt() & 0x7FFFFFFF);
    }

    void handleAsyncClose(final Pooled<ByteBuffer> pooled) {
        ByteBuffer buffer = pooled.getResource();
        int id = buffer.getShort() & 0xffff;
        final OutboundMessage outboundMessage = outboundMessages.get(id);
        if (outboundMessage == null) {
            // ignore; probably harmless...?
            return;
        }
        outboundMessage.remoteClosed();
    }

    public Attachments getAttachments() {
        return attachments;
    }

    public Connection getConnection() {
        return connectionHandlerContext.getConnection();
    }

    @Override
    protected void closeAction() throws IOException {
        closeReadsAndWrites();
        closeMessages();
    }

    private void closeMessages() {
        Executor executor = connection.getExecutor();
        synchronized (connection.getLock()) {
            for (final InboundMessage message : inboundMessages) {
                executor.execute(new Runnable() {
                    public void run() {
                        message.inputStream.pushException(new MessageCancelledException());
                    }
                });
            }
            for (final OutboundMessage message : outboundMessages) {
                executor.execute(new Runnable() {
                    public void run() {
                        message.cancel();
                    }
                });
            }
            for (final InboundMessage message : inboundMessageQueue) {
                executor.execute(new Runnable() {
                    public void run() {
                        message.terminate();
                    }
                });
            }
            inboundMessageQueue.clear();
        }
    }

    RemoteConnection getRemoteConnection() {
        return connection;
    }

    RemoteConnectionHandler getConnectionHandler() {
        return connectionHandler;
    }

    int getChannelId() {
        return channelId;
    }

    void freeInboundMessage(final short id) {
        if (inboundMessages.removeKey(id & 0xffff) != null) {
            closeInboundMessage();
        }
    }

    Pooled<ByteBuffer> allocate(final byte protoId) {
        final Pooled<ByteBuffer> pooled = connection.allocate();
        final ByteBuffer buffer = pooled.getResource();
        buffer.put(protoId);
        buffer.putInt(channelId);
        return pooled;
    }

    public String toString() {
        return String.format("Channel ID %08x (%s) of %s", Integer.valueOf(channelId), (channelId & 0x80000000) == 0 ? "inbound" : "outbound", connection);
    }

    void dumpState(final StringBuilder b) {
        final int state = channelState;
        final int inboundMessageCnt = (state & INBOUND_MESSAGES_MASK) >>> (Integer.numberOfTrailingZeros(ONE_INBOUND_MESSAGE));
        final int outboundMessageCnt = (state & OUTBOUND_MESSAGES_MASK) >>> (Integer.numberOfTrailingZeros(ONE_OUTBOUND_MESSAGE));
        b.append("        ").append(String.format("%s channel ID %08x summary:\n", (channelId & 0x80000000) == 0 ? "Inbound" : "Outbound", channelId));
        b.append("        ").append("* Flags: ");
        if (Bits.allAreSet(state, READ_CLOSED)) b.append("read-closed ");
        if (Bits.allAreSet(state, WRITE_CLOSED)) b.append("write-closed ");
        b.append('\n');
        b.append("        ").append("* ").append(inboundMessageQueue.size()).append(" pending inbound messages\n");
        b.append("        ").append("* ").append(inboundMessageCnt).append(" (max ").append(maxInboundMessages).append(") inbound messages\n");
        b.append("        ").append("* ").append(outboundMessageCnt).append(" (max ").append(maxOutboundMessages).append(") outbound messages\n");
        b.append("        ").append("* Pending inbound messages:\n");
        for (InboundMessage inboundMessage : inboundMessageQueue) {
            inboundMessage.dumpState(b);
        }
        b.append("        ").append("* Inbound messages:\n");
        for (InboundMessage inboundMessage : inboundMessages) {
            inboundMessage.dumpState(b);
        }
        b.append("        ").append("* Outbound messages:\n");
        for (OutboundMessage outboundMessage : outboundMessages) {
            outboundMessage.dumpState(b);
        }
    }
}
