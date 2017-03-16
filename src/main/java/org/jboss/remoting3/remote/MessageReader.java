/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import static org.jboss.remoting3._private.Messages.conn;
import static org.xnio.IoUtils.safeClose;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Iterator;

import org.xnio.Buffers;
import org.xnio.ByteBufferPool;
import org.xnio.ChannelListener;
import org.xnio.Pooled;
import org.xnio.conduits.ConduitStreamSourceChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class MessageReader {

    private final ConduitStreamSourceChannel sourceChannel;
    private final ArrayDeque<ByteBuffer> queue = new ArrayDeque<>();
    private final Object lock;
    private final ByteBuffer[] array = new ByteBuffer[16];

    static final Pooled<ByteBuffer> EOF_MARKER = Buffers.emptyPooledByteBuffer();

    MessageReader(final ConduitStreamSourceChannel sourceChannel, final Object lock) {
        this.sourceChannel = sourceChannel;
        this.lock = lock;
    }

    ConduitStreamSourceChannel getSourceChannel() {
        return sourceChannel;
    }

    Pooled<ByteBuffer> getMessage() throws IOException {
        synchronized (lock) {
            for (;;) {
                ByteBuffer first = queue.peekFirst();
                if (first != null) {
                    if (first.remaining() >= 4) {
                        int size = first.getInt(first.position());
                        if (remaining(size + 4)) {
                            ByteBuffer message = ByteBufferPool.MEDIUM_HEAP.allocate();
                            first.getInt();
                            int cnt = 0;
                            while (cnt < size) {
                                cnt += Buffers.copy(size - cnt, message, first);
                                if (! first.hasRemaining()) {
                                    ByteBufferPool.free(first);
                                    queue.pollFirst();
                                    first = queue.peekFirst();
                                }
                            }
                            message.flip();
                            if (first != null && first.position() + 4 > first.limit()) {
                                // compact & reflip just to make sure there's space for next time
                                first.compact();
                                first.flip();
                            }
                            conn.tracef("Received message %s", message);
                            return Buffers.globalPooledWrapper(message);
                        } else {
                            if (conn.isTraceEnabled()) {
                                conn.tracef("Not enough buffered bytes for message of size %d+4 (%s)", Integer.valueOf(size), first);
                            }
                        }
                    } else {
                        if (queue.peekLast() == first) {
                            // ready for re-filling
                            first.compact().flip();
                        } else {
                            // first can never be full enough; we have to copy a few bytes out of the next one
                            first.compact();
                            try {
                                final Iterator<ByteBuffer> iterator = queue.iterator();
                                iterator.next(); // skip first
                                assert iterator.hasNext(); // at least one more buffer is present because last != first
                                do {
                                    final ByteBuffer next = iterator.next();
                                    if (next.remaining() > 4) {
                                        first.putInt(next.getInt());
                                    } else {
                                        Buffers.copy(first, next);
                                    }
                                } while (first.position() < 4 && iterator.hasNext());
                            } finally {
                                first.flip();
                            }
                        }
                    }
                } else {
                    conn.trace("No buffers in queue for message header");
                }
                ByteBuffer[] b = array;
                ByteBuffer last = queue.pollLast();
                if (last != null) {
                    last.compact();
                    b[0] = last;
                    ByteBufferPool.MEDIUM_DIRECT.allocate(b, 1);
                    conn.tracef("Compacted existing buffer %s", last);
                } else {
                    ByteBufferPool.MEDIUM_DIRECT.allocate(b, 0);
                    conn.tracef("Allocated fresh buffers");
                }
                try {
                    long res = sourceChannel.read(b);
                    if (res == -1) {
                        conn.trace("Received EOF");
                        return EOF_MARKER;
                    }
                    if (res == 0) {
                        conn.trace("No read bytes available");
                        return null;
                    }
                    if (conn.isTraceEnabled()) {
                        conn.tracef("Received %d bytes", Long.valueOf(res));
                    }
                } finally {
                    for (int i = 0; i < b.length; i++) {
                        final ByteBuffer buffer = b[i];
                        if (buffer.position() > 0) {
                            buffer.flip();
                            queue.addLast(buffer);
                        } else {
                            ByteBufferPool.free(buffer);
                        }
                        b[i] = null;
                    }
                }
            }
        }
    }

    private boolean remaining(int cnt) {
        int rem = 0;
        for (ByteBuffer buffer : queue) {
            rem += buffer.remaining();
            if (rem >= cnt) return true;
        }
        return false;
    }

    public void close() {
        synchronized (lock) {
            safeClose(sourceChannel);
            ByteBuffer buffer;
            while ((buffer = queue.pollFirst()) != null) {
                ByteBufferPool.free(buffer);
            }
        }
    }

    public void setReadListener(final ChannelListener<? super ConduitStreamSourceChannel> readListener) {
        synchronized (lock) {
            sourceChannel.setReadListener(readListener);
        }
    }

    public void suspendReads() {
        synchronized (lock) {
            getSourceChannel().suspendReads();
        }
    }

    public void resumeReads() {
        synchronized (lock) {
            getSourceChannel().resumeReads();
        }
    }

    public void wakeupReads() {
        synchronized (lock) {
            getSourceChannel().wakeupReads();
        }
    }

    public void shutdownReads() throws IOException {
        synchronized (lock) {
            getSourceChannel().shutdownReads();
        }
    }
}
