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

import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoUtils;

/**
 * An input stream that reads from byte buffers.  Instances of this class are not safe to use concurrently from
 * multiple threads.
 */
public final class ByteBufferInputStream extends InputStream {
    private final ObjectSource<ByteBuffer> bufferSource;
    private final BufferAllocator<ByteBuffer> allocator;

    private boolean closed;
    private ByteBuffer current;

    public ByteBufferInputStream(final ObjectSource<ByteBuffer> bufferSource, final BufferAllocator<ByteBuffer> allocator) {
        this.bufferSource = bufferSource;
        this.allocator = allocator;
    }

    public int read() throws IOException {
        if (closed) {
            return -1;
        }
        ByteBuffer buffer = getBuffer();
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

    public int read(final byte[] b, int off, int len) throws IOException {
        if (closed) {
            return -1;
        }
        int t = 0;
        while (len > 0) {
            ByteBuffer buffer = getBuffer();
            if (buffer == null) {
                return t == 0 ? -1 : t;
            }
            final int rem = Math.min(len, buffer.remaining());
            if (rem > 0) {
                buffer.get(b, off, rem);
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

    public int available() throws IOException {
        final ByteBuffer buffer = current;
        return (buffer == null ? 0 : buffer.remaining());
    }

    public void close() throws IOException {
        try {
            final ByteBuffer buffer = current;
            current = null;
            if (buffer != null) {
                allocator.free(buffer);
            }
            bufferSource.close();
        } finally {
            closed = true;
            IoUtils.safeClose(bufferSource);
        }
    }

    private ByteBuffer getBuffer() throws IOException {
        final ByteBuffer buffer = current;
        if (buffer == null) {
            while (bufferSource.hasNext()) {
                final ByteBuffer newBuffer = bufferSource.next();
                if (newBuffer.hasRemaining()) {
                    current = newBuffer;
                    return newBuffer;
                } else {
                    allocator.free(newBuffer);
                }
            }
            return null;
        } else {
            return buffer;
        }
    }
}
