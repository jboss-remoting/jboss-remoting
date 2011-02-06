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

package org.jboss.remoting3.newremote;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import org.jboss.remoting3.NotOpenException;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.streams.BufferPipeOutputStream;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class OutboundMessage {
    final short messageId;
    final RemoteChannel channel;
    int window;
    boolean closed;

    OutboundMessage(final short messageId, final RemoteChannel channel, final int window) {
        this.messageId = messageId;
        this.channel = channel;
        this.window = window;
    }

    final BufferPipeOutputStream outputStream = new BufferPipeOutputStream(new BufferPipeOutputStream.BufferWriter() {
        public Pooled<ByteBuffer> getBuffer() throws IOException {
            synchronized (this) {
                if (closed) {
                    throw new NotOpenException("Message was closed asynchronously");
                }
                int window;
                while ((window = OutboundMessage.this.window) == 0) {
                    try {
                        wait();
                        if (closed) {
                            throw new NotOpenException("Message was closed asynchronously");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException();
                    }
                }
                OutboundMessage.this.window = window - 1;
            }
            Pooled<ByteBuffer> pooled = allocate(Protocol.MESSAGE_DATA);
            ByteBuffer buffer = pooled.getResource();
            buffer.put((byte) 0); // flags
            return pooled;
        }

        public void accept(final Pooled<ByteBuffer> pooledBuffer, final boolean eof) throws IOException {
            try {
                final ByteBuffer buffer = pooledBuffer.getResource();
                if (eof) {
                    // EOF flag (sync close)
                    buffer.put(6, (byte) 0x01);
                }
                Channels.sendBlocking(channel.getConnection().getChannel(), buffer);
            } finally {
                pooledBuffer.free();
                if (eof) {
                    channel.freeOutboundMessage(messageId);
                }
            }
        }

        public void flush() throws IOException {
            Channels.flushBlocking(channel.getConnection().getChannel());
        }
    });

    Pooled<ByteBuffer> allocate(byte protoId) {
        Pooled<ByteBuffer> pooled = channel.allocate(protoId);
        ByteBuffer buffer = pooled.getResource();
        buffer.putShort(messageId);
        return pooled;
    }

    void acknowledge() {
        synchronized (this) {
            window ++;
            notify();
        }
    }

    void asyncClose() {
        IoUtils.safeClose(outputStream);
        synchronized (this) {
            closed = true;
            // wake up waiters
            notifyAll();
        }
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }
}
