/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.stream;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoHandler;
import static org.jboss.xnio.IoUtils.nullHandler;
import static org.jboss.xnio.IoUtils.safeClose;
import org.jboss.xnio.channels.ChannelInputStream;
import org.jboss.xnio.channels.StreamChannel;

/**
 * A handler factory for automatic forwarding of output streams.
 */
public final class OutputStreamHandlerFactory implements StreamHandlerFactory<OutputStream, StreamChannel> {

    /** {@inheritDoc} */
    public StreamHandler<OutputStream, StreamChannel> createStreamHandler(final OutputStream localInstance, final StreamContext streamContext) throws IOException {
        return new Handler(localInstance);
    }

    private static final class Handler implements StreamHandler<OutputStream, StreamChannel> {

        private static final long serialVersionUID = 3147719591239403750L;

        private transient final OutputStream localInstance;

        private Handler(final OutputStream instance) {
            localInstance = instance;
        }

        public IoHandler<StreamChannel> getLocalHandler() {
            return new LocalHandler(localInstance);
        }

        public IoHandler<Channel> getRemoteHandler() {
            return nullHandler();
        }

        public OutputStream getRemoteProxy(final IoFuture<? extends StreamChannel> futureChannel) {
            return new ProxyOutputStream(futureChannel);
        }
    }

    private static final class LocalHandler implements IoHandler<StreamChannel> {
        private final OutputStream localInstance;
        private final byte[] bytes = new byte[1024];

        private LocalHandler(final OutputStream instance) {
            localInstance = instance;
        }

        public void handleOpened(final StreamChannel channel) {
        }

        public void handleClosed(final StreamChannel channel) {
            safeClose(localInstance);
        }

        public void handleReadable(final StreamChannel channel) {
            final byte[] bytes = this.bytes;
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            try {
                for (;;) {
                    final int res = channel.read(buffer);
                    if (res == 0) {
                        channel.resumeReads();
                        return;
                    }
                    localInstance.write(bytes, 0, buffer.position());
                    buffer.clear();
                }
            } catch (IOException e) {
                safeClose(channel);
            }
        }

        public void handleWritable(final StreamChannel channel) {
        }
    }

    private static final class ProxyOutputStream extends OutputStream {
        private final ByteBuffer buffer = ByteBuffer.allocate(1024);
        private final IoFuture<? extends StreamChannel> futureChannel;
        private final Lock lock = new ReentrantLock();
        private boolean open = true;

        private ProxyOutputStream(final IoFuture<? extends StreamChannel> channel) {
            futureChannel = channel;
        }

        public void write(final int b) throws IOException {
            final Lock lock = this.lock;
            try {
                lock.lockInterruptibly();
                try {
                    checkOpen();
                    final ByteBuffer buffer = this.buffer;
                    buffer.put((byte) b);
                    if (! buffer.hasRemaining()) {
                        flush();
                    }
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                doInterrupted();
            }
        }

        public void write(final byte[] b, int off, int len) throws IOException {
            final Lock lock = this.lock;
            try {
                lock.lockInterruptibly();
                try {
                    checkOpen();
                    final ByteBuffer buffer = this.buffer;
                    while (len > 0) {
                        final int cnt = min(len, buffer.remaining());
                        buffer.put(b, off, cnt);
                        off += cnt;
                        len -= cnt;
                        if (! buffer.hasRemaining()) {
                            flush();
                        }
                    }
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                doInterrupted();
            }
        }

        public void flush() throws IOException {
            final Lock lock = this.lock;
            try {
                lock.lockInterruptibly();
                try {
                    checkOpen();
                    final StreamChannel channel = futureChannel.get();
                    final ByteBuffer buffer = this.buffer;
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        if (channel.write(buffer) == 0) {
                            channel.awaitWritable();
                        }
                    }
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                doInterrupted();
            }
        }

        public void close() throws IOException {
            final Lock lock = this.lock;
            lock.lock();
            try {
                if (! open) {
                    return;
                }
                final StreamChannel channel = futureChannel.get();
                try {
                    flush();
                    channel.shutdownWrites();
                    final ChannelInputStream is = new ChannelInputStream(channel);
                    int b = is.read();
                    switch (b) {
                        case -1: throw new IOException("Stream outcome unknown");
                        case 0: {
                            final InputStreamReader reader = new InputStreamReader(is, "UTF-8");
                            final StringBuilder builder = new StringBuilder("Remote failure: ");
                            do {
                                b = reader.read();
                                if (b != -1) {
                                    builder.append(b);
                                }
                            } while (b != -1);
                            throw new IOException(builder.toString());
                        }
                        case 1: return;
                        default: throw new IOException("Unknown response from remote host");
                    }
                } finally {
                    safeClose(channel);
                }
            } finally {
                open = false;
                lock.unlock();
            }
        }

        private void doInterrupted() throws InterruptedIOException {
            currentThread().interrupt();
            throw new InterruptedIOException("I/O operation interrupted");
        }

        private void checkOpen() throws IOException {
            if (! open) {
                throw new IOException("Write to closed stream");
            }
        }
    }
}
