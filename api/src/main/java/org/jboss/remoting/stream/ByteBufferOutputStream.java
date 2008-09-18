/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting.stream;

import java.io.OutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Buffers;

/**
 * An output stream that writes to buffers.  Instances of this class are not normally safe to use from multiple threads
 * concurrently.
 */
public final class ByteBufferOutputStream extends OutputStream {
    private final ObjectSink<ByteBuffer> bufferSink;
    private final BufferAllocator<ByteBuffer> allocator;

    private ByteBuffer current;
    private boolean closed;

    /**
     * Construct a new stream instance.
     *
     * @param bufferSink the buffer sink to which full buffers will be written
     * @param allocator the allocator from which empty buffers will be allocated
     */
    public ByteBufferOutputStream(final ObjectSink<ByteBuffer> bufferSink, final BufferAllocator<ByteBuffer> allocator) {
        this.bufferSink = bufferSink;
        this.allocator = allocator;
    }

    private ByteBuffer getBuffer() throws IOException {
        final ByteBuffer buffer = current;
        if (buffer == null) {
            ByteBuffer newbuf = allocator.allocate();
            if (newbuf == null) {
                throw new IOException("No buffers available");
            }
            current = newbuf;
            return newbuf;
        } else {
            return buffer;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void write(final int b) throws IOException {
        if (closed) {
            throw new IOException("Write to closed outputstream");
        }
        final ByteBuffer buffer = getBuffer();
        buffer.put((byte)b);
        if (! buffer.hasRemaining()) {
            localFlush();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void write(final byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Write to closed outputstream");
        }
        do {
            final ByteBuffer buffer = getBuffer();
            final int rem = Math.min(len, buffer.remaining());
            buffer.put(b, off, rem);
            if (! buffer.hasRemaining()) {
                localFlush();
            }
            len -= rem; off += rem;
        } while (len > 0);
    }

    private void localFlush() throws IOException {
        if (closed) {
            throw new IOException("Flush on closed outputstream");
        }
        final ByteBuffer buffer = current;
        if (buffer != null) try {
            bufferSink.accept(Buffers.flip(buffer));
        } finally {
            current = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void flush() throws IOException {
        localFlush();
        bufferSink.flush();
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        if (! closed) try {
            flush();
            bufferSink.close();
        } finally {
            closed = true;
            IoUtils.safeClose(bufferSink);
        }
    }
}
