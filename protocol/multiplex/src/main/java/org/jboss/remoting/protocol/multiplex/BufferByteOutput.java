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

package org.jboss.remoting.protocol.multiplex;

import org.jboss.marshalling.ByteOutput;
import org.jboss.xnio.BufferAllocator;
import java.nio.ByteBuffer;
import java.util.Collection;

/**
 *
 */
public class BufferByteOutput implements ByteOutput {

    private ByteBuffer current;
    private final BufferAllocator<ByteBuffer> allocator;
    private final Collection<ByteBuffer> target;

    public BufferByteOutput(final BufferAllocator<ByteBuffer> allocator, final Collection<ByteBuffer> target) {
        this.allocator = allocator;
        this.target = target;
    }

    private ByteBuffer getCurrent() {
        final ByteBuffer buffer = current;
        return buffer == null ? (current = allocator.allocate()) : buffer;
    }

    public void write(final int i) {
        final ByteBuffer buffer = getCurrent();
        buffer.put((byte) i);
        if (! buffer.hasRemaining()) {
            buffer.flip();
            target.add(buffer);
            current = null;
        }
    }

    public void write(final byte[] bytes) {
        write(bytes, 0, bytes.length);
    }

    public void write(final byte[] bytes, int offs, int len) {
        while (len > 0) {
            final ByteBuffer buffer = getCurrent();
            final int c = Math.min(len, buffer.remaining());
            buffer.put(bytes, offs, c);
            offs += c;
            len -= c;
            if (! buffer.hasRemaining()) {
                buffer.flip();
                target.add(buffer);
                current = null;
            }
        }
    }

    public void close() {
        flush();
    }

    public void flush() {
        final ByteBuffer buffer = current;
        if (buffer != null) {
            buffer.flip();
            target.add(buffer);
            current = null;
        }
    }
}
