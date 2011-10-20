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

import org.jboss.remoting3.MessageOutputStream;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.streams.BufferPipeOutputStream;


/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class OutboundMessage extends MessageOutputStream {
    final short messageId;
    final RemoteConnectionChannel channel;
    final BufferPipeOutputStream pipeOutputStream;
    final int maximumWindow;
    int window;
    boolean closed;
    boolean cancelled;
    final BufferPipeOutputStream.BufferWriter bufferWriter = new BufferPipeOutputStream.BufferWriter() {
        public Pooled<ByteBuffer> getBuffer(boolean firstBuffer) throws IOException {
            Pooled<ByteBuffer> pooled = allocate(Protocol.MESSAGE_DATA);
            ByteBuffer buffer = pooled.getResource();

            //Reserve room for the transmit data which is 4 bytes
            buffer.limit(buffer.limit() - 4);
            
            buffer.put(firstBuffer ? Protocol.MSG_FLAG_NEW : 0); // flags
            // header size plus window size
            int windowPlusHeader = maximumWindow + 8;
            if (buffer.remaining() > windowPlusHeader) {
                // never try to write more than the maximum window size
                buffer.limit(windowPlusHeader);
            }
            return pooled;
        }

        public void accept(final Pooled<ByteBuffer> pooledBuffer, final boolean eof) throws IOException {
            try {
                final ByteBuffer buffer = pooledBuffer.getResource();
                final ConnectedMessageChannel messageChannel = channel.getRemoteConnection().getChannel();
                if (eof) {
                    // EOF flag (sync close)
                    buffer.put(7, (byte)(buffer.get(7) | Protocol.MSG_FLAG_EOF));
                    RemoteLogger.log.tracef("Sending message (with EOF) (%s) to %s", buffer, messageChannel);
                }
                if (cancelled) {
                    buffer.put(7, (byte)(buffer.get(7) | Protocol.MSG_FLAG_CANCELLED));
                    buffer.limit(8); // discard everything in the buffer
                    RemoteLogger.log.trace("Message includes cancel flag");
                }
                synchronized (OutboundMessage.this) {
                    int msgSize = buffer.remaining();
                    window -= msgSize;
                    while (window < msgSize) {
                        try {
                            RemoteLogger.log.trace("Message window is closed, waiting");
                            OutboundMessage.this.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException("Interrupted on write");
                        }
                    }
                    RemoteLogger.log.trace("Message window is open, proceeding with send");
                }
                Channels.sendBlocking(messageChannel, buffer);
            } finally {
                pooledBuffer.free();
                if (eof) {
                    channel.freeOutboundMessage(messageId);
                }
            }
        }

        public void flush() throws IOException {
            RemoteLogger.log.trace("Flushing message channel");
            Channels.flushBlocking(channel.getRemoteConnection().getChannel());
        }
    };

    static final IntIndexer<OutboundMessage> INDEXER = new IntIndexer<OutboundMessage>() {
        public int getKey(final OutboundMessage argument) {
            return argument.messageId & 0xffff;
        }

        public boolean equals(final OutboundMessage argument, final int index) {
            return (argument.messageId & 0xffff) == index;
        }
    };

    OutboundMessage(final short messageId, final RemoteConnectionChannel channel, final int window) {
        this.messageId = messageId;
        this.channel = channel;
        this.window = maximumWindow = window;
        try {
            pipeOutputStream = new BufferPipeOutputStream(bufferWriter);
        } catch (IOException e) {
            // not possible
            throw new IllegalStateException(e);
        }
    }

    Pooled<ByteBuffer> allocate(byte protoId) {
        Pooled<ByteBuffer> pooled = channel.allocate(protoId);
        ByteBuffer buffer = pooled.getResource();
        buffer.putShort(messageId);
        return pooled;
    }

    void acknowledge(int count) {
        synchronized (this) {
            if (RemoteLogger.log.isTraceEnabled()) {
                // do trace enabled check because of boxing here
                RemoteLogger.log.tracef("Acknowledged %d bytes on %s", Integer.valueOf(count), this);
            }
            window += count;
            notifyAll();
        }
    }

    void asyncClose() {
        IoUtils.safeClose(pipeOutputStream);
        channel.free(this);
        synchronized (this) {
            closed = true;
            // wake up waiters
            notifyAll();
        }
    }

    public void write(final int b) throws IOException {
        pipeOutputStream.write(b);
    }

    public void write(final byte[] b) throws IOException {
        pipeOutputStream.write(b);
    }

    public void write(final byte[] b, final int off, final int len) throws IOException {
        pipeOutputStream.write(b, off, len);
    }

    public void flush() throws IOException {
        pipeOutputStream.flush();
    }

    public void close() throws IOException {
        pipeOutputStream.close();
        channel.free(this);
    }

    public MessageOutputStream cancel() {
        cancelled = true;
        IoUtils.safeClose(pipeOutputStream);
        return this;
    }
}
