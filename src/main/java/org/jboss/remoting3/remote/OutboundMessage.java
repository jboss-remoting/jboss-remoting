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

import org.jboss.remoting3.MessageCancelledException;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.NotOpenException;
import org.xnio.BrokenPipeException;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.streams.BufferPipeOutputStream;

import static java.lang.Thread.holdsLock;
import static org.jboss.remoting3.remote.RemoteLogger.log;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class OutboundMessage extends MessageOutputStream {
    final short messageId;
    final RemoteConnectionChannel channel;
    final BufferPipeOutputStream pipeOutputStream;
    final int maximumWindow;
    int window;
    boolean closeCalled;
    boolean closeReceived;
    boolean cancelled;
    boolean cancelSent;
    boolean eofSent;
    boolean released;
    long remaining;
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
            assert holdsLock(pipeOutputStream);
            if (closeCalled) {
                throw new NotOpenException("Message was closed asynchronously by another thread");
            }
            if (cancelSent) {
                throw new MessageCancelledException("Message was cancelled");
            }
            if (closeReceived) {
                throw new BrokenPipeException("Remote side closed the message stream");
            }
            if (eof) {
                closeCalled = true;
                // make sure other waiters know about it
                pipeOutputStream.notifyAll();
            }
            final ByteBuffer buffer = pooledBuffer.getResource();
            final ConnectedMessageChannel messageChannel = channel.getRemoteConnection().getChannel();
            final boolean badMsgSize = channel.getConnectionHandler().isFaultyMessageSize();
            final int msgSize = badMsgSize ? buffer.remaining() : buffer.remaining() - 8;
            boolean sendCancel = cancelled && ! cancelSent;
            boolean intr = false;
            if (msgSize > 0 && ! sendCancel) {
                // empty messages and cancellation both bypass the transmit window check
                for (;;) {
                    if (window > msgSize) {
                        window -= msgSize;
                        log.trace("Message window is open, proceeding with send");
                        break;
                    }
                    try {
                        log.trace("Message window is closed, waiting");
                        pipeOutputStream.wait();
                    } catch (InterruptedException e) {
                        cancelled = true;
                        intr = true;
                        break;
                    }
                    if (closeReceived) {
                        throw new BrokenPipeException("Remote side closed the message stream");
                    }
                    if (closeCalled && ! eof) {
                        throw new NotOpenException("Message was closed asynchronously by another thread");
                    }
                    if (cancelSent) {
                        throw new MessageCancelledException("Message was cancelled");
                    }
                }
            }
            if (eof || sendCancel || intr) {
                // EOF flag (sync close)
                eofSent = true;
                buffer.put(7, (byte) (buffer.get(7) | Protocol.MSG_FLAG_EOF));
                log.tracef("Sending message (with EOF) (%s) to %s", buffer, messageChannel);
                if (! channel.getConnectionHandler().isMessageClose()) {
                    // free now, because we may never receive a close message
                    channel.free(OutboundMessage.this);
                }
                if (! released) {
                    released = true;
                    channel.closeOutboundMessage();
                }
            }
            if (sendCancel || intr) {
                cancelSent = true;
                buffer.put(7, (byte) (buffer.get(7) | Protocol.MSG_FLAG_CANCELLED));
                buffer.limit(8); // discard everything in the buffer so we can send even if there is no window
                log.trace("Message includes cancel flag");
            }
            channel.getRemoteConnection().send(pooledBuffer);
            if (intr) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted on write (message cancelled)");
            }
        }

        public void flush() throws IOException {
            log.trace("Flushing message channel");
            // no op
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

    OutboundMessage(final short messageId, final RemoteConnectionChannel channel, final int window, final long maxOutboundMessageSize) {
        this.messageId = messageId;
        this.channel = channel;
        this.window = maximumWindow = window;
        this.remaining = maxOutboundMessageSize;
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
        synchronized (pipeOutputStream) {
            if (log.isTraceEnabled()) {
                // do trace enabled check because of boxing here
                log.tracef("Acknowledged %d bytes on %s", Integer.valueOf(count), this);
            }
            window += count;
            pipeOutputStream.notifyAll();
        }
    }

    void remoteClosed() {
        synchronized (pipeOutputStream) {
            closeReceived = true;
            Pooled<ByteBuffer> pooled = pipeOutputStream.breakPipe();
            if (pooled != null) {
                pooled.free();
            }
            if (! eofSent && channel.getConnectionHandler().isMessageClose()) {
                eofSent = true;
                pooled = allocate(Protocol.MESSAGE_DATA);
                final ByteBuffer buffer = pooled.getResource();
                buffer.put(Protocol.MSG_FLAG_EOF); // flags
                buffer.flip();
                channel.getRemoteConnection().send(pooled);
            }
            // safe to free now; remote side has cleared this ID for sure
            // if the peer is new, then they send this to free the message either way
            // if the peer is old, then they only send this if they're using the broken async close protocol, and they've already dropped the ID
            // either way if this was already freed then it's OK as this is idempotent
            channel.free(this);
            if (! released) {
                released = true;
                channel.closeOutboundMessage();
            }
            // wake up waiters
            pipeOutputStream.notifyAll();
        }
    }

    public void write(final int b) throws IOException {
        if (remaining > 1) {
            pipeOutputStream.write(b);
            remaining--;
        } else {
            throw overrun();
        }
    }

    private IOException overrun() {
        try {
            return new IOException("Maximum message size overrun");
        } finally {
            cancel();
        }
    }

    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void write(final byte[] b, final int off, final int len) throws IOException {
        if ((long) len > remaining) {
            throw overrun();
        }
        pipeOutputStream.write(b, off, len);
        remaining -= len;
    }

    public void flush() throws IOException {
        pipeOutputStream.flush();
    }

    public void close() throws IOException {
        synchronized (pipeOutputStream) {
            pipeOutputStream.notifyAll();
            pipeOutputStream.close();
        }
    }

    public MessageOutputStream cancel() {
        synchronized (pipeOutputStream) {
            cancelled = true;
            pipeOutputStream.notifyAll();
            IoUtils.safeClose(pipeOutputStream);
            return this;
        }
    }

    public String toString() {
        return String.format("Outbound message ID %04x on %s", Short.valueOf(messageId), channel);
    }

    void dumpState(final StringBuilder b) {
        b.append("            ").append(String.format("Outbound message ID %04x, window %d of %d\n", messageId & 0xFFFF, window, maximumWindow));
        b.append("            ").append("* flags: ");
        if (cancelled) b.append("cancelled ");
        if (cancelSent) b.append("cancel-sent ");
        if (closeReceived) b.append("close-received ");
        if (closeCalled) b.append("closed-called ");
        if (eofSent) b.append("eof-sent ");
        b.append('\n');
    }
}
