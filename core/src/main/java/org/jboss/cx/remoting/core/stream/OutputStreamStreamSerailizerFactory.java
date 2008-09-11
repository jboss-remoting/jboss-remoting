package org.jboss.cx.remoting.core.stream;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.spi.stream.StreamContext;
import org.jboss.cx.remoting.util.OrderedExecutor;
import org.jboss.xnio.channels.AllocatedMessageChannel;
import org.jboss.xnio.channels.WritableMessageChannel;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.ChannelSource;
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

    private BufferAllocator<ByteBuffer> allocator;

    public BufferAllocator<ByteBuffer> getAllocator() {
        return allocator;
    }

    public void setAllocator(final BufferAllocator<ByteBuffer> allocator) {
        this.allocator = allocator;
    }

    public IoHandler<? super AllocatedMessageChannel> getLocalSide(final Object localSide, final StreamContext streamContext) throws IOException {
        return new LocalHandler((OutputStream) localSide, allocator, new OrderedExecutor(streamContext.getExecutor()));
    }

    public Object getRemoteSide(final ChannelSource<AllocatedMessageChannel> remoteClient, final StreamContext streamContext) throws IOException {
        final RemoteHandler handler = new RemoteHandler(allocator);
        final IoFuture<AllocatedMessageChannel> futureChannel = remoteClient.open(handler);
        return new RemoteOutputStream(handler, futureChannel, allocator);
    }

    public static final class LocalHandler implements IoHandler<AllocatedMessageChannel> {

        private final OutputStream outputStream;
        private final BufferAllocator<ByteBuffer> allocator;
        private final Executor executor;
        private volatile String exceptionMessage;

        public LocalHandler(final OutputStream outputStream, final BufferAllocator<ByteBuffer> allocator, final Executor executor) {
            this.outputStream = outputStream;
            this.allocator = allocator;
            this.executor = executor;
        }

        public void handleOpened(final AllocatedMessageChannel channel) {
            channel.resumeReads();
        }

        public void handleReadable(final AllocatedMessageChannel channel) {
            try {
                for (;;) {
                    final ByteBuffer buffer = channel.receive();
                    if (buffer == null) {
                        IoUtils.safeClose(channel);
                        log.trace("Remote output stream closed normally");
                    } else if (! buffer.hasRemaining()) {
                        channel.resumeReads();
                        return;
                    } else {
                        buffer.flip();
                        executor.execute(new Runnable() {
                            public void run() {
                                try {
                                    if (buffer.hasArray()) {
                                        outputStream.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
                                    } else {
                                        final byte[] bytes = new byte[buffer.remaining()];
                                        buffer.get(bytes);
                                        outputStream.write(bytes);
                                    }
                                    channel.resumeReads();
                                } catch (Throwable t) {
                                    exceptionMessage = t.getMessage();
                                    channel.resumeWrites();
                                    try {
                                        channel.shutdownReads();
                                    } catch (Throwable tt) {
                                        log.warn(tt, "Unable to shutdown reads on a channel");
                                    }
                                } finally {
                                    allocator.free(buffer);
                                }
                            }
                        });
                    }
                }
            } catch (IOException e) {
                IoUtils.safeClose(channel);
                log.trace("Remote output stream closed due to exception: %s", e.getMessage());
            } finally {
            }
        }

        public void handleWritable(final AllocatedMessageChannel channel) {
            final String msg = exceptionMessage;
            if (msg == null) {
                // spurious...
                return;
            }
            try {
                final ByteBuffer buffer = ByteBuffer.wrap(msg.getBytes("utf-8"));
                if (! channel.send(buffer)) {
                    channel.resumeWrites();
                    return;
                }
            } catch (UnsupportedEncodingException e) {
                // should not happen; if it does, just close the channel
            } catch (IOException e) {
                // nothing we can do about it
            }
            IoUtils.safeClose(channel);
            exceptionMessage = null;
        }

        public void handleClosed(final AllocatedMessageChannel channel) {
            IoUtils.safeClose(outputStream);
        }
    }

    public static final class RemoteHandler implements IoHandler<AllocatedMessageChannel> {

        private final Object lock = new Object();
        private final BufferAllocator<ByteBuffer> allocator;

        private boolean closed;
        private ByteBuffer buffer;
        private IOException exception;

        private RemoteHandler(final BufferAllocator<ByteBuffer> allocator) {
            this.allocator = allocator;
        }

        public void pushBuffer(final WritableMessageChannel channel, final ByteBuffer buffer) throws IOException {
            synchronized (lock) {
                final IOException exception = this.exception;
                if (exception != null) {
                    this.exception = null;
                    IOException ioe = new IOException("Write failed");
                    ioe.initCause(exception);
                    throw ioe;
                }
                if (closed) {
                    throw new IOException("Channel closed");
                }
                while (this.buffer != null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException("Operation interrupted");
                    }
                }
                if (! channel.send(buffer)) {
                    channel.resumeWrites();
                    this.buffer = buffer;
                }
            }
        }

        public void handleOpened(final AllocatedMessageChannel channel) {
            channel.resumeReads();
            synchronized (lock) {
                if (buffer != null) {
                    channel.resumeWrites();
                }
            }
        }

        public void handleReadable(final AllocatedMessageChannel channel) {
            try {
                final ByteBuffer buffer = channel.receive();
                if (buffer == null) {
                    // normal close
                    IoUtils.safeClose(channel);
                }
            } catch (IOException e) {
                exception = new IOException("Received unexpected I/O exception");
                exception.initCause(e);
                IoUtils.safeClose(channel);
            }
        }

        public void handleWritable(final AllocatedMessageChannel channel) {
            synchronized (lock) {
                final ByteBuffer buffer = this.buffer;
                if (buffer == null) {
                    return;
                }
                try {
                    if (channel.send(buffer)) {
                        allocator.free(buffer);
                        this.buffer = null;
                    }
                } catch (IOException e) {
                    exception = e;
                    IoUtils.safeClose(channel);
                }
            }
        }

        public void handleClosed(final AllocatedMessageChannel channel) {
            closed = true;
            final ByteBuffer buffer = this.buffer;
            if (buffer != null) {
                this.buffer = null;
                allocator.free(buffer);
            }
        }
    }

    public static final class RemoteOutputStream extends OutputStream {

        private final RemoteHandler handler;
        private final IoFuture<? extends WritableMessageChannel> futureChannel;
        private final Object lock = new Object();
        private final BufferAllocator<ByteBuffer> allocator;
        private ByteBuffer buffer;

        public RemoteOutputStream(final RemoteHandler handler, final IoFuture<? extends WritableMessageChannel> futureChannel, final BufferAllocator<ByteBuffer> allocator) {
            this.handler = handler;
            this.futureChannel = futureChannel;
            this.allocator = allocator;
            synchronized (lock) {
                buffer = allocator.allocate();
            }
        }

        public void write(final int b) throws IOException {
            synchronized (lock) {
                final ByteBuffer buffer = this.buffer;
                if (buffer == null) {
                    throw new IOException("Channel closed");
                }
                buffer.put((byte)b);
                if (! buffer.hasRemaining()) {
                    flush();
                }
            }
        }

        public void write(final byte[] bytes, int offset, int length) throws IOException {
            synchronized (lock) {
                while (length > 0) {
                    final ByteBuffer buffer = this.buffer;
                    if (buffer == null) {
                        throw new IOException("Channel closed");
                    }
                    int size = Math.min(buffer.remaining(), length);
                    buffer.put(bytes, offset, size);
                    length -= size; offset += size;
                    if (! buffer.hasRemaining()) {
                        flush();
                    }
                }
            }
        }

        public void flush() throws IOException {
            synchronized (lock) {
                if (doFlush()) {
                    buffer = allocator.allocate();
                }
            }
        }

        private boolean doFlush() throws IOException {
            final ByteBuffer buffer = this.buffer;
            if (buffer != null) {
                handler.pushBuffer(futureChannel.get(), buffer);
                return true;
            } else {
                return false;
            }
        }

        public void close() throws IOException {
            final Channel channel;
            try {
                channel = futureChannel.get();
                if (channel == null) {
                    return;
                }
            } catch (IOException ex) {
                // throwing this exception would cause close() to appear to not be idempotent
                log.trace("No channel to close: %s", ex.getMessage());
                return;
            }
            try {
                synchronized (lock) {
                    doFlush();
                    buffer = null;
                }
                channel.close();
            } finally {
                IoUtils.safeClose(channel);
            }
        }
    }
}
