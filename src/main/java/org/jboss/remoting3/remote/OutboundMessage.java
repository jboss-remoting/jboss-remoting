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

import static java.lang.System.nanoTime;
import static java.lang.Thread.holdsLock;
import static org.jboss.remoting3._private.Messages.log;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;

import org.jboss.remoting3.MessageCancelledException;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.NotOpenException;
import org.xnio.BrokenPipeException;
import org.xnio.Connection;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.streams.BufferPipeOutputStream;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class OutboundMessage extends MessageOutputStream {
    private final short messageId;
    private final RemoteConnectionChannel channel;
    private final BufferPipeOutputStream pipeOutputStream;
    private final int maximumWindow;
    private final long ackTimeout;
    private int window;
    private boolean closeCalled;
    private boolean closeReceived;
    private boolean cancelled;
    private boolean cancelSent;
    private boolean eofSent;
    private boolean released;
    private long remaining;

    private final BufferPipeOutputStream.BufferWriter bufferWriter = new BufferPipeOutputStream.BufferWriter() {
        public Pooled<ByteBuffer> getBuffer(boolean firstBuffer) throws IOException {
            Pooled<ByteBuffer> pooled = allocate(Protocol.MESSAGE_DATA);
            boolean ok = false;
            try {
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
                ok = true;
                return pooled;
            } finally {
                if (! ok) pooled.free();
            }
        }

        public void accept(final Pooled<ByteBuffer> pooledBuffer, final boolean eof) throws IOException {
            boolean ok = false;
            try {
                assert holdsLock(pipeOutputStream);
                if (closeCalled) {
                    throw new NotOpenException(this + ": message was closed asynchronously by another thread");
                }
                if (cancelSent) {
                    throw new MessageCancelledException(this + ": message was cancelled");
                }
                if (closeReceived) {
                    throw new BrokenPipeException(this + ": remote side closed the message stream");
                }
                if (eof) {
                    closeCalled = true;
                    // make sure other waiters know about it
                    pipeOutputStream.notifyAll();
                }
                final ByteBuffer buffer = pooledBuffer.getResource();
                final Connection connection = channel.getRemoteConnection().getConnection();
                final boolean badMsgSize = channel.getConnectionHandler().isFaultyMessageSize();
                final int msgSize = badMsgSize ? buffer.remaining() : buffer.remaining() - 8;
                boolean sendCancel = cancelled && ! cancelSent;
                boolean intr = false;
                boolean timeoutExpired = false;
                if (msgSize > 0 && ! sendCancel) {
                    // empty messages and cancellation both bypass the transmit window check
                    if (!decrementWindow(msgSize)) {
                        final long initialTime = nanoTime();
                        long ackTimeout = OutboundMessage.this.ackTimeout;
                        do {
                            try {
                                log.tracef("Outbound message ID %04x: message window is closed, waiting", getActualId());
                                pipeOutputStream.wait(ackTimeout / 1_000_000, (int) (ackTimeout % 1_000_000));
                            } catch (InterruptedException e) {
                                cancelled = true;
                                intr = true;
                                break;
                            }
                            if (closeReceived) {
                                throw new BrokenPipeException(this + ": remote side closed the message stream");
                            }
                            if (closeCalled && ! eof) {
                                throw new NotOpenException(this + ": message was closed asynchronously by another thread");
                            }
                            if (cancelSent) {
                                throw new MessageCancelledException(this + ": message was cancelled");
                            }
                        } while (!decrementWindow(msgSize) &&
                                (ackTimeout = OutboundMessage.this.ackTimeout - (nanoTime() - initialTime)) > 0);
                        if (ackTimeout <= 0) {
                            timeoutExpired = true;
                        }
                    }
                }
                if (eof || sendCancel || intr || timeoutExpired) {
                    // EOF flag (sync close)
                    eofSent = true;
                    buffer.put(7, (byte) (buffer.get(7) | Protocol.MSG_FLAG_EOF));
                    log.tracef("Outbound message ID %04x: sending message (with EOF) (%s) to %s", getActualId(), buffer, connection);
                    if (! channel.getConnectionHandler().isMessageClose()) {
                        // free now, because we may never receive a close message
                        channel.free(OutboundMessage.this);
                    }
                    if (! released) {
                        released = true;
                        channel.closeOutboundMessage();
                    }
                }
                if (sendCancel || intr || timeoutExpired) {
                    cancelSent = true;
                    buffer.put(7, (byte) (buffer.get(7) | Protocol.MSG_FLAG_CANCELLED));
                    buffer.limit(8); // discard everything in the buffer so we can send even if there is no window
                    log.tracef("Outbound message ID %04x: message includes cancel flag", getActualId());
                }
                if (timeoutExpired) {
                    remoteClosed();
                }
                channel.getRemoteConnection().send(pooledBuffer);
                ok = true;
                if (intr) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException(this + ": interrupted on write (message cancelled)");
                }
                if (timeoutExpired) {
                    throw new IOException(this + ": cancelled because ack timeout has expired, no acks for this message received from client within " + ackTimeout + " milliseconds");
                }
            } finally {
                if (! ok) pooledBuffer.free();
            }
        }

        private final boolean decrementWindow(long messageSize) {
            if (window >= messageSize) {
                window -= messageSize;
                if (log.isTraceEnabled()) {
                    log.tracef("Outbound message ID %04x: message window is open (%d-%d=%d remaining), proceeding with send",
                            getActualId(), window + messageSize, messageSize, window);
                }
                return true;
            }
            return false;
        }

        public void flush() throws IOException {
            log.tracef("Outbound message ID %04x: flushing message channel", getActualId());
            // no op
        }
    };

    static final ToIntFunction<OutboundMessage> INDEXER = OutboundMessage::getActualId;

    OutboundMessage(final short messageId, final RemoteConnectionChannel channel, final int window, final long maxOutboundMessageSize, final long ackTimeout) {
        this.messageId = messageId;
        this.channel = channel;
        this.window = maximumWindow = window;
        this.ackTimeout = ackTimeout;
        this.remaining = maxOutboundMessageSize;
        try {
            pipeOutputStream = new BufferPipeOutputStream(bufferWriter);
        } catch (IOException e) {
            // not possible
            throw new IllegalStateException(e);
        }
    }

    int getActualId() {
        return messageId & 0xffff;
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
                log.tracef("%s: acknowledged %d bytes", this, Integer.valueOf(count));
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
                boolean ok = false;
                try {
                    final ByteBuffer buffer = pooled.getResource();
                    buffer.put(Protocol.MSG_FLAG_EOF); // flags
                    buffer.flip();
                    channel.getRemoteConnection().send(pooled);
                    ok = true;
                } finally {
                    if (! ok) pooled.free();
                }
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
        try {
            if (remaining > 1) {
                pipeOutputStream.write(b);
                remaining--;
            } else {
                throw overrun();
            }
        } catch (IOException e) {
            cancel();
            throw e;
        }
    }

    private IOException overrun() {
        try {
            return new IOException(this + ": maximum message size overrun");
        } finally {
            cancel();
        }
    }

    public void write(final byte[] b) throws IOException {
        try {
            write(b, 0, b.length);
        } catch (IOException e) {
            cancel();
            throw e;
        }
    }

    public void write(final byte[] b, final int off, final int len) throws IOException {
        try {
            if ((long) len > remaining) {
                throw overrun();
            }
            pipeOutputStream.write(b, off, len);
            remaining -= len;
        } catch (IOException e) {
            cancel();
            throw e;
        }
    }

    public void flush() throws IOException {
        try {
            pipeOutputStream.flush();
        } catch (IOException e) {
            cancel();
            throw e;
        }
    }

    public void close() throws IOException {
        try {
            synchronized (pipeOutputStream) {
                pipeOutputStream.notifyAll();
                pipeOutputStream.close();
            }
        } catch (IOException e) {
            cancel();
            throw e;
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
        return String.format("Outbound message ID %04x on %s", getActualId(), channel);
    }

    void dumpState(final StringBuilder b) {
        synchronized (pipeOutputStream) {
            b.append("            ").append(String.format("Outbound message ID %04x, window %d of %d\n", getActualId(), window, maximumWindow));
            b.append("            ").append("* flags: ");
            if (cancelled) b.append("cancelled ");
            if (cancelSent) b.append("cancel-sent ");
            if (closeReceived) b.append("close-received ");
            if (closeCalled) b.append("closed-called ");
            if (eofSent) b.append("eof-sent ");
            b.append('\n');
        }
    }
}
