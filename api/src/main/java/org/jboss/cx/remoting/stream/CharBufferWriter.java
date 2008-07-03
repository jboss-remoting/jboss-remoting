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

package org.jboss.cx.remoting.stream;

import java.io.IOException;
import java.io.Writer;
import java.nio.CharBuffer;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Buffers;

/**
 * A writer that writes to buffers.  Instances of this class are not normally safe to use from multiple threads
 * concurrently.
 */
public final class CharBufferWriter extends Writer {
    private final ObjectSink<CharBuffer> bufferSink;
    private final BufferAllocator<CharBuffer> allocator;

    private CharBuffer current;
    private boolean closed;

    /**
     * Construct a new stream instance.
     *
     * @param bufferSink the buffer sink to which full buffers will be written
     * @param allocator the allocator from which empty buffers will be allocated
     */
    public CharBufferWriter(final ObjectSink<CharBuffer> bufferSink, final BufferAllocator<CharBuffer> allocator) {
        this.bufferSink = bufferSink;
        this.allocator = allocator;
    }

    private CharBuffer getBuffer() throws IOException {
        final CharBuffer buffer = current;
        if (buffer == null) {
            CharBuffer newbuf = allocator.allocate();
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
            throw new IOException("Write to closed writer");
        }
        final CharBuffer buffer = getBuffer();
        buffer.put((char)b);
        if (! buffer.hasRemaining()) {
            localFlush();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void write(final char[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Write to closed writer");
        }
        do {
            final CharBuffer buffer = getBuffer();
            final int rem = Math.min(len, buffer.remaining());
            buffer.put(b, off, rem);
            if (! buffer.hasRemaining()) {
                localFlush();
            }
            len -= rem; off += rem;
        } while (len > 0);
    }

    /**
     * {@inheritDoc}
     */
    public void write(final String str, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Write to closed writer");
        }
        do {
            final CharBuffer buffer = getBuffer();
            final int rem = Math.min(len, buffer.remaining());
            buffer.put(str, off, off + rem);
            if (! buffer.hasRemaining()) {
                localFlush();
            }
            len -= rem; off += rem;
        } while (len > 0);
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

    private void localFlush() throws IOException {
        if (closed) {
            throw new IOException("Flush on closed writer");
        }
        final CharBuffer buffer = current;
        if (buffer != null) try {
            bufferSink.accept(Buffers.flip(buffer));
        } finally {
            current = null;
        }
    }
}