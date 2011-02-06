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
import java.nio.ByteBuffer;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.streams.BufferPipeInputStream;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class InboundMessage {
    final short messageId;
    final RemoteChannel channel;
    int inboundWindow;
    boolean closed;

    InboundMessage(final short messageId, final RemoteChannel channel) {
        this.messageId = messageId;
        this.channel = channel;
    }

    BufferPipeInputStream inputStream = new BufferPipeInputStream(new BufferPipeInputStream.InputHandler() {
        public void acknowledgeMessage() throws IOException {
            Pooled<ByteBuffer> pooled = allocate(Protocol.MESSAGE_WINDOW_OPEN);
            try {
                ByteBuffer buffer = pooled.getResource();
                buffer.put((byte) 1); // Open window by one
                buffer.flip();
                Channels.sendBlocking(channel.getConnection().getChannel(), buffer);
            } finally {
                pooled.free();
            }
            openInboundWindow();
        }

        public void close() throws IOException {
            sendAsyncClose();
        }
    });

    void sendAsyncClose() throws IOException {
        Pooled<ByteBuffer> pooled = allocate(Protocol.MESSAGE_ASYNC_CLOSE);
        try {
            ByteBuffer buffer = pooled.getResource();
            buffer.flip();
            Channels.sendBlocking(channel.getConnection().getChannel(), buffer);
        } finally {
            pooled.free();
        }
    }

    Pooled<ByteBuffer> allocate(byte protoId) {
        Pooled<ByteBuffer> pooled = channel.allocate(protoId);
        ByteBuffer buffer = pooled.getResource();
        buffer.putShort(messageId);
        return pooled;
    }

    void openInboundWindow() {
        synchronized (this) {
            inboundWindow++;
        }
    }

    void closeInboundWindow() {
        synchronized (this) {
            inboundWindow--;
        }
    }

    void handleIncoming(Pooled<ByteBuffer> pooledBuffer) {
        synchronized (this) {
            if (closed) {
                // ignore
                pooledBuffer.free();
                return;
            }
            if (inboundWindow == 0) {
                pooledBuffer.free();
                // TODO log window overrun
                try {
                    sendAsyncClose();
                } catch (IOException e) {
                    // todo log it
                }
                return;
            }
            ByteBuffer buffer = pooledBuffer.getResource();
            byte flags = buffer.get();
            inputStream.push(pooledBuffer);
            if ((flags & 0x01) != 0) {
                inputStream.pushEof();
                closed = true;
                channel.freeInboundMessage(messageId);
            }
        }
    }
}
