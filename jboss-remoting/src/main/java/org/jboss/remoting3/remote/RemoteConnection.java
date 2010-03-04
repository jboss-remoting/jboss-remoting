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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import org.jboss.marshalling.ProviderDescriptor;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Pool;
import org.jboss.xnio.channels.Channels;
import org.jboss.xnio.channels.ConnectedStreamChannel;
import org.jboss.xnio.channels.MessageHandler;

final class RemoteConnection extends AbstractHandleableCloseable<RemoteConnection> implements Closeable {
    private final ConnectedStreamChannel<InetSocketAddress> channel;
    private final ProviderDescriptor providerDescriptor;
    private final Pool<ByteBuffer> bufferPool = Buffers.createHeapByteBufferAllocator(4096);
    private final MessageHandler.Setter messageHandlerSetter;
    private final OptionMap optionMap;
    private final Object writeLock = new Object();

    RemoteConnection(final Executor executor, final ConnectedStreamChannel<InetSocketAddress> channel, final OptionMap optionMap, final ProviderDescriptor providerDescriptor) {
        super(executor);
        this.channel = channel;
        this.providerDescriptor = providerDescriptor;
        messageHandlerSetter = Channels.createMessageReader(channel, optionMap);
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

    ConnectedStreamChannel<InetSocketAddress> getChannel() {
        return channel;
    }

    ByteBuffer allocate() {
        return bufferPool.allocate();
    }

    void free(ByteBuffer buffer) {
        bufferPool.free(buffer);
    }

    void setMessageHandler(MessageHandler handler) {
        messageHandlerSetter.set(handler);
    }

    void sendBlocking(final ByteBuffer buffer, boolean flush) throws IOException {
        try {
            synchronized (writeLock) {
                buffer.putInt(0, buffer.remaining() - 4);
                boolean intr = false;
                try {
                    while (buffer.hasRemaining()) {
                        if (channel.write(buffer) == 0) {
                            try {
                                channel.awaitWritable();
                            } catch (InterruptedIOException e) {
                                intr = Thread.interrupted();
                            }
                        }
                    }
                    if (flush) while (! channel.flush()) {
                        try {
                            channel.awaitWritable();
                        } catch (InterruptedIOException e) {
                            intr = Thread.interrupted();
                        }
                    }
                } finally {
                    if (intr) Thread.currentThread().interrupt();
                }
            }
        } catch (IOException e) {
            RemoteConnectionHandler.log.trace(e, "Closing channel due to failure to send");
            IoUtils.safeClose(channel);
            throw e;
        } catch (RuntimeException e) {
            RemoteConnectionHandler.log.trace(e, "Closing channel due to failure to send");
            IoUtils.safeClose(channel);
            throw e;
        } catch (Error e) {
            RemoteConnectionHandler.log.trace(e, "Closing channel due to failure to send");
            IoUtils.safeClose(channel);
            throw e;
        }
    }

    void flushBlocking() throws IOException {
        synchronized (writeLock) {
            try {
                while (! channel.flush()) {
                    channel.awaitWritable();
                }
            } catch (IOException e) {
                RemoteConnectionHandler.log.trace(e, "Closing channel due to failure to flush");
                IoUtils.safeClose(channel);
                throw e;
            } catch (RuntimeException e) {
                RemoteConnectionHandler.log.trace(e, "Closing channel due to failure to flush");
                IoUtils.safeClose(channel);
                throw e;
            } catch (Error e) {
                RemoteConnectionHandler.log.trace(e, "Closing channel due to failure to flush");
                IoUtils.safeClose(channel);
                throw e;
            }
        }
    }

    void shutdownWritesBlocking() throws IOException {
        synchronized (writeLock) {
            try {
                while (! channel.shutdownWrites()) {
                    channel.awaitWritable();
                }
            } catch (IOException e) {
                RemoteConnectionHandler.log.trace(e, "Closing channel due to failure to shutdown writes");
                IoUtils.safeClose(channel);
                throw e;
            } catch (RuntimeException e) {
                RemoteConnectionHandler.log.trace(e, "Closing channel due to failure to shutdown writes");
                IoUtils.safeClose(channel);
                throw e;
            } catch (Error e) {
                RemoteConnectionHandler.log.trace(e, "Closing channel due to failure to shutdown writes");
                IoUtils.safeClose(channel);
                throw e;
            }
        }
    }

    void sendAuthReject(final String msg) throws IOException {
        final ByteBuffer buf = allocate();
        try {
            buf.putInt(0);
            buf.put(RemoteProtocol.AUTH_REJECTED);
            Buffers.putModifiedUtf8(buf, msg);
            buf.flip();
            sendBlocking(buf, true);
        } finally {
            free(buf);
        }
    }

    void sendAuthMessage(final byte msgType, final byte[] message) throws IOException {
        final ByteBuffer buf = allocate();
        try {
            buf.putInt(0);
            buf.put(msgType);
            if (message != null) buf.put(message);
            buf.flip();
            sendBlocking(buf, true);
        } finally {
            free(buf);
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
            RemoteConnectionHandler.log.trace("Channel terminate exception: %s", e);
        }
    }
}
