/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.remote;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import org.jboss.marshalling.ProviderDescriptor;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.xnio.BufferAllocator;
import org.xnio.Buffers;
import org.xnio.ByteBufferSlicePool;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedStreamChannel;
import org.jboss.logging.Logger;

final class RemoteConnection extends AbstractHandleableCloseable<RemoteConnection> implements Closeable {
    private final ConnectedStreamChannel channel;
    private final ProviderDescriptor providerDescriptor;
    private final Pool<ByteBuffer> bufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 4096, 2097152);
    private final OptionMap optionMap;
    private final Object writeLock = new Object();
    private static final Logger log = Loggers.main;

    RemoteConnection(final Executor executor, final ConnectedStreamChannel channel, final OptionMap optionMap, final ProviderDescriptor providerDescriptor) {
        super(executor);
        this.channel = channel;
        this.providerDescriptor = providerDescriptor;
        this.optionMap = optionMap;
    }

    protected void closeAction() throws IOException {
        synchronized (writeLock) {
            try {
                shutdownWritesBlocking();
            } catch (IOException e) {
                IoUtils.safeClose(channel);
                return;
            }
        }
    }

    OptionMap getOptionMap() {
        return optionMap;
    }

    ConnectedStreamChannel getChannel() {
        return channel;
    }

    Pooled<ByteBuffer> allocate() {
        final Pooled<ByteBuffer> pooled = bufferPool.allocate();
        // Leave room for the "size" header
        pooled.getResource().position(2);
        return pooled;
    }

    void sendAuthReject(final String msg) throws IOException {
        final Pooled<ByteBuffer> pooled = allocate();
        try {
            final ByteBuffer buf = pooled.getResource();
            buf.put(RemoteProtocol.AUTH_REJECTED);
            Buffers.putModifiedUtf8(buf, msg);
            buf.flip();
            sendBlocking(buf, true);
            Channels.writeBlocking(channel, buf);
            Channels.flushBlocking(channel);
        } finally {
            pooled.free();
        }
    }

    void sendBlocking(final ByteBuffer buf, final boolean flush) throws IOException {
        buf.putShort(0, (short) buf.remaining());
        final ConnectedStreamChannel channel = this.channel;
        Channels.writeBlocking(channel, buf);
        if (flush) {
            Channels.flushBlocking(channel);
        }
    }

    void sendAuthMessage(final byte msgType, final byte[] message) throws IOException {
        final Pooled<ByteBuffer> pooled = allocate();
        try {
            final ByteBuffer buf = pooled.getResource();
            buf.put(msgType);
            if (message != null) buf.put(message);
            buf.flip();
            sendBlocking(pooled, true);
        } finally {
            pooled.free();
        }
    }

    void shutdownReads() throws IOException {
        channel.shutdownReads();
    }

    ProviderDescriptor getProviderDescriptor() {
        return providerDescriptor;
    }

    void terminate() {
        try {
            channel.close();
        } catch (IOException e) {
            log.trace("Channel terminate exception: %s", e);
        }
    }
}
