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

package org.jboss.remoting.protocol.basic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.channels.WritableMessageChannel;

/**
 *
 */
public final class SimpleWriteHandler implements WriteHandler {
    private static final Logger log = Logger.getLogger(SimpleWriteHandler.class);

    private final BufferAllocator<ByteBuffer> allocator;
    private final ByteBuffer[] buffers;

    public SimpleWriteHandler(final BufferAllocator<ByteBuffer> allocator, final List<ByteBuffer> buffers) {
        this.allocator = allocator;
        this.buffers = buffers.toArray(new ByteBuffer[buffers.size()]);
        logBufferSize();
    }

    public SimpleWriteHandler(final BufferAllocator<ByteBuffer> allocator, final ByteBuffer[] buffers) {
        this.allocator = allocator;
        this.buffers = buffers;
        logBufferSize();
    }

    public SimpleWriteHandler(final BufferAllocator<ByteBuffer> allocator, final ByteBuffer buffer) {
        this.allocator = allocator;
        buffers = new ByteBuffer[] { buffer };
        logBufferSize();
    }

    private void logBufferSize() {
        if (log.isTrace()) {
            long t = 0L;
            for (ByteBuffer buf : buffers) {
                t += (long)buf.remaining();
            }
            log.trace("Writing a message of size %d", Long.valueOf(t));
        }
    }

    public boolean handleWrite(final WritableMessageChannel channel) {
        boolean done = true;
        try {
            return (done = channel.send(buffers));
        } catch (IOException e) {
            log.trace(e, "Write failed");
            return true;
        } finally {
            if (done) {
                for (ByteBuffer buffer : buffers) {
                    allocator.free(buffer);
                }
            }
        }
    }
}
