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

package org.jboss.cx.remoting.core.marshal;

import java.io.OutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;

/**
 *
 */
public final class OneBufferOutputStream extends OutputStream {

    private ByteBuffer buffer;
    private final Object lock;

    public OneBufferOutputStream(final Object lock) {
        this.lock = lock;
    }

    public void setBuffer(ByteBuffer buffer) {
        synchronized (lock) {
            if (this.buffer != null) {
                throw new IllegalStateException("Buffer already set");
            }
            this.buffer = buffer;
            lock.notify();
        }
    }

    private ByteBuffer getBuffer() throws InterruptedIOException {
        synchronized (lock) {
            for (;;) {
                final ByteBuffer buffer = this.buffer;
                if (buffer != null) {
                    if (! buffer.hasRemaining()) {
                        lock.notify();
                        this.buffer = null;
                    } else {
                        return buffer;
                    }
                }
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("getBuffer() operation interrupted!");
                }
            }
        }
    }

    public void write(final int b) throws IOException {
        synchronized (lock) {
            final ByteBuffer buffer = getBuffer();
            buffer.put((byte) b);
        }
    }

    public void write(byte[] b, int off, int len) throws IOException {
        synchronized (lock) {
            while (len > 0) {
                final ByteBuffer buffer = getBuffer();
                final int rem = Math.min(buffer.remaining(), len);
                buffer.put(b, off, rem);
                len -= rem; off += rem;
            }
        }
    }

    public void flush() throws IOException {
        synchronized (lock) {
            buffer = null;
            lock.notify();
        }
    }

    public void close() throws IOException {
        flush();
    }

    public void await() {
        synchronized (lock) {
            while (buffer != null) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    buffer = null;
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
