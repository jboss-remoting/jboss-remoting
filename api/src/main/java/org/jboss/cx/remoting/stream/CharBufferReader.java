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
import java.io.Reader;
import java.nio.CharBuffer;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoUtils;

/**
 * A reader that reads from char buffers.  Instances of this class are not safe to use concurrently from
 * multiple threads.
 */
public final class CharBufferReader extends Reader {
    private final ObjectSource<CharBuffer> bufferSource;
    private final BufferAllocator<CharBuffer> allocator;

    private boolean closed;
    private CharBuffer current;

    public CharBufferReader(final ObjectSource<CharBuffer> bufferSource, final BufferAllocator<CharBuffer> allocator) {
        this.bufferSource = bufferSource;
        this.allocator = allocator;
    }

    /**
     * {@inheritDoc}
     */
    public int read() throws IOException {
        if (closed) {
            return -1;
        }
        CharBuffer buffer = getBuffer();
        if (buffer == null) {
            return -1;
        }
        try {
            return buffer.get() & 0xff;
        } finally {
            if (! buffer.hasRemaining()) {
                current = null;
                allocator.free(buffer);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public int read(final char[] cbuf, int off, int len) throws IOException {
        if (closed) {
            return -1;
        }
        int t = 0;
        while (len > 0) {
            CharBuffer buffer = getBuffer();
            if (buffer == null) {
                return t == 0 ? -1 : t;
            }
            final int rem = Math.min(len, buffer.remaining());
            if (rem > 0) {
                buffer.get(cbuf, off, rem);
                off += rem;
                len -= rem;
                t += rem;
            }
            if (! buffer.hasRemaining()) {
                current = null;
                allocator.free(buffer);
            }
        }
        return t;
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws IOException {
        try {
            final CharBuffer buffer = current;
            current = null;
            allocator.free(buffer);
            bufferSource.close();
        } finally {
            closed = true;
            IoUtils.safeClose(bufferSource);
        }
    }

    /**
     * {@inheritDoc}
     */
    public int read(final CharBuffer target) throws IOException {
        if (closed) {
            return -1;
        }
        int t = 0;
        int len = target.remaining();
        while (len > 0) {
            CharBuffer buffer = getBuffer();
            if (buffer == null) {
                return t == 0 ? -1 : t;
            }
            final int rem = Math.min(len, buffer.remaining());
            if (rem > 0) {
                buffer.read(target);
                len -= rem;
                t += rem;
            }
            if (! buffer.hasRemaining()) {
                current = null;
                allocator.free(buffer);
            }
        }
        return t;
    }

    /**
     * {@inheritDoc}
     */
    public boolean ready() throws IOException {
        final CharBuffer buffer = current;
        return buffer != null && buffer.hasRemaining();
    }

    private CharBuffer getBuffer() throws IOException {
        final CharBuffer buffer = current;
        if (buffer == null) {
            if (bufferSource.hasNext()) {
                final CharBuffer newBuffer = bufferSource.next();
                current = newBuffer;
                return newBuffer;
            } else {
                return null;
            }
        } else {
            return buffer;
        }
    }
}