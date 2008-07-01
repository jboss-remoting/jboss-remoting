package org.jboss.cx.remoting.core.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.concurrent.Semaphore;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.Client;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.log.Logger;

/**
 * Stream serializer for {@link java.io.OutputStream} instances.
 */
public final class OutputStreamStreamSerailizerFactory implements StreamSerializerFactory {

    private static final Logger log = Logger.getLogger(OutputStreamStreamSerailizerFactory.class);

    private static final long serialVersionUID = -5934238025840749071L;

    public IoHandler<? super StreamChannel> getLocalSide(final Object localSide) throws IOException {
        return new LocalHandler((OutputStream) localSide, new BufferAllocator<ByteBuffer>() {
            public ByteBuffer allocate() {
                return ByteBuffer.allocate(512);
            }

            public void free(final ByteBuffer byteBuffer) {
            }
        });
    }

    public Object getRemoteSide(final Client<StreamChannel> remoteClient) throws IOException {
        final RemoteHandler handler = new RemoteHandler(new BufferAllocator<ByteBuffer>() {
            public ByteBuffer allocate() {
                return ByteBuffer.allocate(512);
            }

            public void free(final ByteBuffer byteBuffer) {
            }
        });
        final IoFuture<StreamChannel> futureChannel = remoteClient.connect(handler);
        return new RemoteOutputStream(handler, futureChannel);
    }

    public static final class LocalHandler implements IoHandler<StreamSourceChannel> {

        private final OutputStream outputStream;
        private final BufferAllocator<ByteBuffer> allocator;

        public LocalHandler(final OutputStream outputStream, final BufferAllocator<ByteBuffer> allocator) {
            this.outputStream = outputStream;
            this.allocator = allocator;
        }

        public void handleOpened(final StreamSourceChannel channel) {
            channel.resumeReads();
        }

        public void handleReadable(final StreamSourceChannel channel) {
            ByteBuffer buffer = allocator.allocate();
            try {
                for (;; buffer.clear()) {
                    final int c = channel.read(buffer);
                    if (c == 0) {
                        channel.resumeReads();
                        return;
                    } else if (c == -1) {
                        IoUtils.safeClose(channel);
                        log.trace("Remote output stream closed normally");
                    } else {
                        buffer.flip();
                        if (buffer.hasArray()) {
                            outputStream.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
                        } else {
                            final byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);
                            outputStream.write(bytes);
                        }
                    }
                }
            } catch (IOException e) {
                IoUtils.safeClose(channel);
                log.trace("Remote output stream closed due to exception: %s", e.getMessage());
            } finally {
                allocator.free(buffer);
            }
        }

        public void handleWritable(final StreamSourceChannel channel) {
        }

        public void handleClosed(final StreamSourceChannel channel) {
            IoUtils.safeClose(outputStream);
        }
    }

    public static final class RemoteHandler implements IoHandler<StreamSinkChannel> {

        private final Semaphore semaphore = new Semaphore(0);
        private final BufferAllocator<ByteBuffer> allocator;

        private volatile boolean closed;
        private volatile ByteBuffer buffer;

        private RemoteHandler(final BufferAllocator<ByteBuffer> allocator) {
            this.allocator = allocator;
        }

        public void handleOpened(final StreamSinkChannel channel) {
            // block sends until the channel is up
            semaphore.release();
        }

        public void handleReadable(final StreamSinkChannel channel) {
        }

        public void handleWritable(final StreamSinkChannel channel) {
            final ByteBuffer buffer = this.buffer;
            if (buffer != null) {
                try {
                    while (buffer.hasRemaining()) {
                        if (channel.write(buffer) == 0) {
                            channel.resumeWrites();
                            return;
                        }
                    }
                } catch (IOException e) {
                    log.trace("Send exception: %s", e.getMessage());
                    IoUtils.safeClose(channel);
                    semaphore.release();
                }
                this.buffer = null;
                allocator.free(buffer);
            }
        }

        public void handleClosed(final StreamSinkChannel channel) {
            closed = true;
            final ByteBuffer buffer = this.buffer;
            if (buffer != null) {
                this.buffer = null;
                allocator.free(buffer);
            }
        }

        private void send(final ByteBuffer buffer) throws IOException {
            if (closed) {
                throw new IOException("Channel closed");
            }
            semaphore.acquireUninterruptibly();
            if (closed) {
                semaphore.release();
                allocator.free(buffer);
                throw new IOException("Channel closed");
            }
            this.buffer = buffer;
        }
    }

    public static final class RemoteOutputStream extends OutputStream {

        private final RemoteHandler handler;
        private final IoFuture<? extends Channel> futureChannel;
        private ByteBuffer buffer;

        public RemoteOutputStream(final RemoteHandler handler, final IoFuture<? extends Channel> futureChannel) {
            this.handler = handler;
            this.futureChannel = futureChannel;
        }

        public void write(final int b) throws IOException {
            if (handler.closed) {
                throw new IOException("Channel closed");
            }
            if (buffer == null) {
                buffer = handler.allocator.allocate();
            }
            buffer.put((byte)b);
            if (! buffer.hasRemaining()) {
                flush();
            }
        }

        public void write(final byte[] bytes, int offset, int length) throws IOException {
            if (handler.closed) {
                throw new IOException("Channel closed");
            }
            if (buffer == null) {
                buffer = handler.allocator.allocate();
            }
            while (length > 0) {
                int size = Math.min(buffer.remaining(), length);
                buffer.put(bytes, offset, size);
                length -= size; offset += size;
                if (! buffer.hasRemaining()) {
                    flush();
                }
            }
        }

        public void flush() throws IOException {
            try {
                handler.send(buffer);
            } finally {
                buffer = null;
            }
        }

        public void close() throws IOException {
            final Channel channel;
            try {
                channel = futureChannel.get();
            } catch (IOException ex) {
                // throwing this exception would cause close() to appear to not be idempotent
                log.trace("No channel to close: %s", ex.getMessage());
                return;
            }
            channel.close();
        }
    }
}
