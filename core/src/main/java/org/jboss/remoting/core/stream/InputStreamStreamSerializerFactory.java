package org.jboss.remoting.core.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import org.jboss.xnio.log.Logger;
import org.jboss.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.remoting.spi.stream.StreamContext;
import org.jboss.xnio.channels.AllocatedMessageChannel;
import org.jboss.xnio.channels.WritableMessageChannel;
import org.jboss.xnio.channels.ReadableAllocatedMessageChannel;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoFuture;
import static org.jboss.xnio.Buffers.skip;
import static org.jboss.xnio.Buffers.flip;

/**
 * An input stream serializer.  The input stream transfer protocol consists of two types of "chunks": data and error.
 * A data chunk starts with an ASCII {@code 'd'}, followed by the actual data.  An error chunk starts with an ASCII
 * {@code 'e'} followed by a series of UTF-8 bytes representing a description of the error, followed by the end of
 * the stream.
 *
 * Normally data chunks are transferred over the stream until the original {@link InputStream} is exhausted, at which time
 * the proxy stream will return a {@code -1} for the EOF condition.
 */
public final class InputStreamStreamSerializerFactory implements StreamSerializerFactory {

    private static final long serialVersionUID = -3198623117987624799L;
    private static final Logger log = org.jboss.xnio.log.Logger.getLogger(InputStreamStreamSerializerFactory.class);

    private BufferAllocator<ByteBuffer> allocator;

    public InputStreamStreamSerializerFactory() {
        // no-arg constructor required
    }

    public IoHandler<? super AllocatedMessageChannel> getLocalSide(final Object localSide, final StreamContext streamContext) throws IOException {
        return new LocalHandler((InputStream) localSide, allocator, streamContext.getExecutor());
    }

    public Object getRemoteSide(final ChannelSource<AllocatedMessageChannel> remoteClient, final StreamContext streamContext) throws IOException {
        final RemoteHandler handler = new RemoteHandler();
        return new RemoteInputStream(remoteClient.open(handler), allocator, handler);
    }

    public BufferAllocator<ByteBuffer> getAllocator() {
        return allocator;
    }

    public void setAllocator(final BufferAllocator<ByteBuffer> allocator) {
        this.allocator = allocator;
    }

    private static final int DATA_CHUNK = 'd';
    private static final int ERROR = 'e';

    public static final class LocalHandler implements IoHandler<WritableMessageChannel> {

        private final Object lock = new Object();
        private final Executor executor;
        private final BufferAllocator<ByteBuffer> allocator;
        private final Runnable fillTask = new FillTask();

        // @protectedby {@code lock}
        private WritableMessageChannel channel;
        // @protectedby {@code lock}
        private final InputStream inputStream;
        // @protectedby {@code lock}
        private ByteBuffer writing;
        // @protectedby {@code lock}
        private boolean eof;

        private LocalHandler(final InputStream inputStream, final BufferAllocator<ByteBuffer> allocator, final Executor executor) {
            this.inputStream = inputStream;
            this.allocator = allocator;
            this.executor = executor;
        }

        public void handleOpened(final WritableMessageChannel channel) {
            this.channel = channel;
            executor.execute(fillTask);
        }

        public void handleReadable(final WritableMessageChannel channel) {
            // not called on a sink channel
        }

        public void handleWritable(final WritableMessageChannel channel) {
            synchronized (lock) {
                final ByteBuffer buffer = writing;
                if (buffer == null) {
                    if (eof) {
                        IoUtils.safeClose(channel);
                    } else {
                        executor.execute(fillTask);
                    }
                } else {
                    final boolean sent;
                    try {
                        sent = channel.send(buffer);
                    } catch (IOException e) {
                        log.debug("Channel write failed: %s", e);
                        IoUtils.safeClose(channel);
                        return;
                    }
                    if (sent) {
                        writing = null;
                        allocator.free(buffer);
                        executor.execute(fillTask);
                    } else {
                        channel.resumeWrites();
                    }
                }
            }
        }

        public void handleClosed(final WritableMessageChannel channel) {
            synchronized (this) {
                IoUtils.safeClose(inputStream);
            }
        }

        private final class FillTask implements Runnable {
            public void run() {
                try {
                    final ByteBuffer buffer = allocator.allocate();
                    buffer.put((byte) DATA_CHUNK);
                    buffer.putShort((short) 0);
                    final int rem = buffer.remaining();
                    final int cnt;
                    if (buffer.hasArray()) {
                        final byte[] a = buffer.array();
                        final int off = buffer.arrayOffset();
                        cnt = inputStream.read(a, off, rem);
                        if (cnt == -1) {
                            synchronized (lock) {
                                eof = true;
                                return;
                            }
                        }
                        skip(buffer, cnt);
                    } else {
                        final byte[] a = new byte[rem];
                        cnt = inputStream.read(a);
                        if (cnt == -1) {
                            synchronized (lock) {
                                eof = true;
                                return;
                            }
                        }
                        buffer.put(a);
                    }
                    buffer.putShort(1, (short) cnt);
                    synchronized (lock) {
                        writing = flip(buffer);
                    }
                    channel.resumeWrites();
                    return;
                } catch (IOException e) {
                    synchronized (lock) {
                        eof = true;
                        try {
                            // this could probably be improved upon
                            writing = ByteBuffer.wrap((Character.toString((char) ERROR) + e.getMessage()).getBytes("utf-8"));
                        } catch (UnsupportedEncodingException e1) {
                            writing = ByteBuffer.wrap(new byte[] { ERROR });
                        }
                    }
                }
            }
        }
    }

    public static final class RemoteHandler implements IoHandler<ReadableAllocatedMessageChannel> {

        private final Object lock = new Object();

        private ByteBuffer current;
        private boolean done;
        private IOException exception;

        private RemoteHandler() {
        }

        public ByteBuffer getBuffer() throws IOException {
            synchronized (lock) {
                if (exception != null) {
                    final IOException ex = new IOException("I/O exception from channel receive");
                    ex.initCause(exception);
                    throw ex;
                }
                try {
                    while (current == null && ! done) {
                        lock.wait();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Interrupted while reading from input stream");
                }
                try {
                    return current;
                } finally {
                    current = null;
                }
            }
        }

        public void handleOpened(final ReadableAllocatedMessageChannel channel) {
            channel.resumeReads();
        }

        public void handleReadable(final ReadableAllocatedMessageChannel channel) {
            synchronized (lock) {
                if (current != null) {
                    return;
                }
                try {
                    final ByteBuffer buffer = channel.receive();
                    if (buffer == null) {
                        IoUtils.safeClose(channel);
                        return;
                    }
                    if (! buffer.hasRemaining()) {
                        channel.resumeReads();
                        return;
                    }
                    final byte type = buffer.get();
                    switch (type) {
                        case DATA_CHUNK: {
                            current = buffer;
                            // only one waiter would be able to use this anyway
                            lock.notify();
                            break;
                        }
                        case ERROR: {
                            if (buffer.hasArray()) {
                                IoUtils.safeClose(channel);
                                final byte[] a = buffer.array();
                                final int offs = buffer.arrayOffset();
                                final int rem = buffer.remaining();
                                exception = new IOException(new String(a, offs + 1, rem, "utf-8"));
                            }
                            break;
                        }
                        default: {
                            IoUtils.safeClose(channel);
                            exception = new IOException("Remote data stream was corrupted");
                            break;
                        }
                    }
                } catch (IOException e) {
                    IoUtils.safeClose(channel);
                    // should only be one waiter, but just in case, notify em all so they all catch the exception...
                    exception = e;
                }
            }
        }

        public void handleWritable(final ReadableAllocatedMessageChannel channel) {
            // empty
        }

        public void handleClosed(final ReadableAllocatedMessageChannel channel) {
            synchronized (lock) {
                done = true;
                lock.notifyAll();
            }
        }
    }

    public static final class RemoteInputStream extends InputStream {

        private final BufferAllocator<ByteBuffer> allocator;

        private final Object lock = new Object();

        // @protectedby lock
        private ByteBuffer current;

        private final IoFuture<? extends ReadableAllocatedMessageChannel> futureChannel;
        private final RemoteHandler handler;

        private RemoteInputStream(final IoFuture<? extends ReadableAllocatedMessageChannel> futureChannel, final BufferAllocator<ByteBuffer> allocator, final RemoteHandler handler) {
            this.futureChannel = futureChannel;
            this.allocator = allocator;
            this.handler = handler;
        }

        // call under {@code lock}
        private ByteBuffer getCurrent() throws IOException {
            if (current != null) {
                return current;
            } else {
                final ByteBuffer buffer = handler.getBuffer();
                if (buffer != null) {
                    current = buffer;
                    return buffer;
                } else {
                    return null;
                }
            }
        }

        public int read() throws IOException {
            synchronized (lock) {
                final ByteBuffer buffer = getCurrent();
                if (buffer == null) {
                    return -1;
                }
                final byte v = buffer.get();
                if (! buffer.hasRemaining()) {
                    current = null;
                    allocator.free(buffer);
                }
                return v & 0xff;
            }
        }

        public int read(final byte[] b, final int off, final int len) throws IOException {
            synchronized (lock) {
                final ByteBuffer buffer = getCurrent();
                if (buffer == null) {
                    return -1;
                }
                final int cnt = Math.min(buffer.remaining(), len);
                buffer.get(b, off, cnt);
                if (! buffer.hasRemaining()) {
                    current = null;
                    allocator.free(buffer);
                }
                return cnt;
            }
        }

        public void close() throws IOException {
            synchronized (lock) {
                if (current != null) {
                    allocator.free(current);
                    current = null;
                }
                futureChannel.get().close();
            }
        }

        public int available() throws IOException {
            synchronized (lock) {
                return current == null ? 0 : current.remaining();
            }
        }
    }
}