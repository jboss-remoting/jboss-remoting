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
import java.util.concurrent.Executor;

import org.jboss.remoting3.Attachments;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedMessageChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteConnectionChannel extends AbstractHandleableCloseable<Channel> implements Channel {

    static final IntIndexer<RemoteConnectionChannel> INDEXER = new IntIndexer<RemoteConnectionChannel>() {
        public int indexOf(final RemoteConnectionChannel argument) {
            return argument.channelId;
        }

        public boolean equals(final RemoteConnectionChannel argument, final int index) {
            return argument.channelId == index;
        }
    };

    private final RemoteConnection connection;
    private final int channelId;
    private final UnlockedReadIntIndexHashMap<OutboundMessage> outboundMessages = new UnlockedReadIntIndexHashMap<OutboundMessage>(OutboundMessage.INDEXER);
    private final UnlockedReadIntIndexHashMap<InboundMessage> inboundMessages = new UnlockedReadIntIndexHashMap<InboundMessage>(InboundMessage.INDEXER);
    private final Random random;
    private final int outboundWindow;
    private final int inboundWindow;
    private final Attachments attachments = new Attachments();
    private final Queue<InboundMessage> inboundMessageQueue = new ArrayDeque<InboundMessage>();
    private Receiver nextReceiver;
    private int outboundMessageCount;
    private boolean writeClosed;
    private boolean readClosed;

    RemoteConnectionChannel(final Executor executor, final RemoteConnection connection, final int channelId, final Random random, final int outboundWindow, final int inboundWindow, final int outboundMessageCount, final int inboundMessageCount) {
        super(executor);
        this.connection = connection;
        this.channelId = channelId;
        this.random = random;
        this.outboundWindow = outboundWindow;
        this.inboundWindow = inboundWindow;
        this.outboundMessageCount = outboundMessageCount;
    }

    public MessageOutputStream writeMessage() throws IOException {
        int tries = 50;
        UnlockedReadIntIndexHashMap<OutboundMessage> outboundMessages = this.outboundMessages;
        synchronized (connection) {
            if (writeClosed) {
                throw log.channelNotOpen();
            }
            int messageCount;
            while ((messageCount = outboundMessageCount) == 0) {
                try {
                    connection.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw log.writeInterrupted();
                }
            }
            final Random random = this.random;
            while (tries > 0) {
                final int id = random.nextInt() & 0xfffe;
                if (! outboundMessages.containsKey(id)) {
                    OutboundMessage message = new OutboundMessage((short) id, this, outboundWindow);
                    outboundMessages.put(message);
                    outboundMessageCount = messageCount - 1;
                    return message;
                }
                tries --;
            }
            throw log.channelBusy();
        }
    }
    
    void free(OutboundMessage outboundMessage) {
        synchronized (connection) {
            outboundMessages.remove(outboundMessage);
            outboundMessageCount++;
            connection.notifyAll();
        }
    }

    public void writeShutdown() throws IOException {
        synchronized (connection) {
            if (writeClosed) {
                return;
            }
            writeClosed = true;
            Pooled<ByteBuffer> pooled = connection.allocate();
            try {
                ByteBuffer byteBuffer = pooled.getResource();
                byteBuffer.put(Protocol.CHANNEL_SHUTDOWN_WRITE);
                byteBuffer.putInt(channelId);
                byteBuffer.flip();
                ConnectedMessageChannel channel = connection.getChannel();
                Channels.sendBlocking(channel, byteBuffer);
                Channels.flushBlocking(channel);
            } finally {
                pooled.free();
            }
        }
    }

    void handleRemoteClose() {
        synchronized (connection) {
            writeClosed = true;
            if (readClosed) {
                return;
            }
            readClosed = true;
            for (OutboundMessage message : outboundMessages) {
                message.asyncClose();
            }
        }
    }

    void handleWriteShutdown() {
        final Receiver receiver;
        final Runnable runnable;
        synchronized (connection) {
            receiver = nextReceiver;
            if (receiver != null) {
                runnable = new Runnable() {
                    public void run() {
                        try {
                            receiver.handleEnd(RemoteConnectionChannel.this);
                        } catch (Throwable t) {
                            log.exceptionInUserHandler(t);
                        }
                    }
                };
            } else {
                return;
            }
        }
        Executor executor = connection.getExecutor();
        executor.execute(runnable);
    }

    public void receiveMessage(final Receiver handler) {
        synchronized (connection) {
            if (inboundMessageQueue.isEmpty()) {
                if (nextReceiver != null) {
                    throw new IllegalStateException("Message handler already queued");
                }
                nextReceiver = handler;
            } else {
                final InboundMessage message = inboundMessageQueue.remove();
                getExecutor().execute(new Runnable() {
                    public void run() {
                        handler.handleMessage(RemoteConnectionChannel.this, message.messageInputStream);
                    }
                });
            }
            connection.notify();
        }
    }

    void handleMessageData(final Pooled<ByteBuffer> message) {
        ByteBuffer buffer = message.getResource();
        int id = buffer.getShort() & 0xffff;
        int flags = buffer.get() & 0xff;
        final InboundMessage inboundMessage;
        if ((flags & Protocol.MSG_FLAG_NEW) != 0) {
            inboundMessage = new InboundMessage((short) id, this, inboundWindow);
            if (inboundMessages.putIfAbsent(inboundMessage) != null) {
                connection.handleException(new IOException("Protocol error: incoming message with duplicate ID received"));
                return;
            }
            synchronized(connection) {
                if (nextReceiver != null) {
                    final Receiver receiver = nextReceiver;
                    nextReceiver = null;
                    getExecutor().execute(new Runnable() {
                        public void run() {
                            receiver.handleMessage(RemoteConnectionChannel.this, inboundMessage.messageInputStream);
                        }
                    });
                } else {
                    inboundMessageQueue.add(inboundMessage);
                }
            }
        } else {
            inboundMessage = inboundMessages.get(id);
            if (inboundMessage == null) {
                connection.handleException(new IOException("Protocol error: incoming message with unknown ID received"));
                return;
            }
        }
        inboundMessage.handleIncoming(message);
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
        outboundMessage.asyncClose();
    }

    public Attachments getAttachments() {
        return attachments;
    }

    @Override
    protected void closeAction() throws IOException {
        writeShutdown();
        handleRemoteClose();
    }

    RemoteConnection getConnection() {
        return connection;
    }

    int getChannelId() {
        return channelId;
    }

    void freeOutboundMessage(final short id) {
        outboundMessages.remove(id & 0xffff);
    }

    void freeInboundMessage(final short id) {
        inboundMessages.remove(id & 0xffff);
    }

    Pooled<ByteBuffer> allocate(final byte protoId) {
        final Pooled<ByteBuffer> pooled = connection.allocate();
        final ByteBuffer buffer = pooled.getResource();
        buffer.put(protoId);
        buffer.putInt(channelId);
        return pooled;
    }
}
