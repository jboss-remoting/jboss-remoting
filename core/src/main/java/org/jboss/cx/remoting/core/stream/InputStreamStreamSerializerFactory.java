package org.jboss.cx.remoting.core.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import org.jboss.xnio.log.Logger;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.core.util.DecodingBuilder;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.Client;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoFuture;
import static org.jboss.xnio.Buffers.skip;
import static org.jboss.xnio.Buffers.flip;

/**
 * An input stream serializer.  The input stream transfer protocol consists of two types of "chunks": data and error.
 * A data chunk starts with an ASCII {@code 'd'}, followed by a two-byte (unsigned) length field (a value of
 * {@code 0x0000} indicates a 65536-byte chunk), followed by the actual data.  An error chunk consists of a series of
 * UTF-8 bytes representing a description of the error, followed by the end of the stream.
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

    public IoHandler<StreamSinkChannel> getLocalSide(final Object localSide) throws IOException {
        return new LocalHandler((InputStream) localSide, allocator);
    }

    public Object getRemoteSide(final Client<StreamChannel> remoteClient) throws IOException {
//        return new RemoteInputStream(taskList, futureChannel);
        return null;
    }

    public BufferAllocator<ByteBuffer> getAllocator() {
        return allocator;
    }

    public void setAllocator(final BufferAllocator<ByteBuffer> allocator) {
        this.allocator = allocator;
    }

    private static final byte DATA_CHUNK = (byte) 'd';
    private static final byte ERROR = (byte) 'e';

    public static final class LocalHandler implements IoHandler<StreamSinkChannel> {

        // @protectedby {@code this}
        private final InputStream inputStream;
        private final BufferAllocator<ByteBuffer> allocator;
        private volatile ByteBuffer current;
        private volatile boolean eof;

        private LocalHandler(final InputStream inputStream, final BufferAllocator<ByteBuffer> allocator) {
            this.inputStream = inputStream;
            this.allocator = allocator;
        }

        private boolean fillBuffer() throws IOException {
            final ByteBuffer buffer = allocator.allocate();
            buffer.put(DATA_CHUNK);
            buffer.putShort((short) 0);
            final int cnt;
            if (buffer.hasArray()) {
                final byte[] a = buffer.array();
                final int off = buffer.arrayOffset();
                final int rem = Math.min(buffer.remaining(), 65536);
                cnt = inputStream.read(a, off, rem);
                if (cnt == -1) {
                    return false;
                }
                skip(current, cnt);
            } else {
                final int rem = Math.min(buffer.remaining(), 65536);
                final byte[] a = new byte[rem];
                cnt = inputStream.read(a);
                if (cnt == -1) {
                    return false;
                }
                current.put(a);
            }
            buffer.putShort(1, (short) cnt);
            current = flip(buffer);
            return true;
        }

        private void prepareChunk(final StreamSinkChannel channel) {
            try {
                eof = fillBuffer();
            } catch (Throwable e) {
                try {
                    current = ByteBuffer.wrap(("e" + e.getMessage()).getBytes("utf-8"));
                } catch (UnsupportedEncodingException e1) {
                    current = ByteBuffer.wrap(new byte[] { ERROR });
                }
                eof = true;
            }
            channel.resumeWrites();
        }

        public void handleOpened(final StreamSinkChannel channel) {
            if (channel.getOptions().contains(CommonOptions.TCP_NODELAY)) {
                try {
                    channel.setOption(CommonOptions.TCP_NODELAY, Boolean.TRUE);
                } catch (IOException e) {
                    // not too big a deal; just skip it
                    log.trace(e, "Failed to enable TCP_NODELAY");
                }
            }
            prepareChunk(channel);
        }

        public void handleReadable(final StreamSinkChannel channel) {
            // not called on a sink channel
        }

        public void handleWritable(final StreamSinkChannel channel) {
            while (current.hasRemaining()) {
                try {
                    final int c = channel.write(current);
                    if (c == 0) {
                        channel.resumeWrites();
                        return;
                    }
                } catch (IOException e) {
                    log.debug("Channel write failed: %s", e);
                    IoUtils.safeClose(channel);
                }
            }
            if (eof) {
                IoUtils.safeClose(channel);
            } else {
                prepareChunk(channel);
            }
        }

        public void handleClosed(final StreamSinkChannel channel) {
            synchronized (this) {
                IoUtils.safeClose(inputStream);
            }
        }
    }

    public static final class RemoteHandler implements IoHandler<StreamSourceChannel> {

        private enum DecoderState {
            NEW_CHUNK,
            IN_ERROR,
            IN_DATA,
        }

        private final RemoteInputStream remoteInputStream;
        private final ByteBuffer initialBuffer = ByteBuffer.allocate(5);

        private volatile ByteBuffer dataBuffer = null;

        private volatile DecodingBuilder exceptionBuilder;
        private volatile DecoderState decoderState = DecoderState.NEW_CHUNK;

        private RemoteHandler(final RemoteInputStream remoteInputStream, final BufferAllocator<ByteBuffer> allocator) {
            this.remoteInputStream = remoteInputStream;
        }

        public void handleOpened(final StreamSourceChannel channel) {
            channel.resumeReads();
        }

        public void handleReadable(final StreamSourceChannel channel) {
            try {
                for (;;) switch (decoderState) {
                    case NEW_CHUNK: {
                        int n = channel.read(initialBuffer);
                        if (n == -1) {
                            IoUtils.safeClose(channel);
                            return;
                        }
                        if (n == 0) {
                            remoteInputStream.scheduleResumeReads(channel);
                            return;
                        }
                        if (initialBuffer.get(0) == DATA_CHUNK) {
                            if (initialBuffer.hasRemaining()) {
                                handleReadable(channel);
                                return;
                            }
                            initialBuffer.flip();
                            initialBuffer.get();
                            final int length = (initialBuffer.getShort() - 1) & 0xffff + 1;
                            dataBuffer = ByteBuffer.allocate(length);
                            decoderState = DecoderState.IN_DATA;
                            break;
                        } else if (initialBuffer.get(0) == ERROR) {
                            decoderState = DecoderState.IN_ERROR;
                            initialBuffer.flip();
                            initialBuffer.get();
                            exceptionBuilder.append(initialBuffer);
                            initialBuffer.clear();
                            break;
                        } else {
                            remoteInputStream.acceptException("Received garbage from remote side");
                            IoUtils.safeClose(channel);
                            return;
                        }
                    }
                    case IN_ERROR: {
                        ByteBuffer buffer = ByteBuffer.allocate(256);
                        int n = channel.read(buffer);
                        if (n == -1) {
                            remoteInputStream.acceptException(exceptionBuilder.finish().toString());
                            exceptionBuilder = null;
                            IoUtils.safeClose(channel);
                            return;
                        }
                        if (n == 0) {
                            remoteInputStream.scheduleResumeReads(channel);
                            return;
                        }
                        exceptionBuilder.append(buffer);
                        break;
                    }
                    case IN_DATA: {
                        if (! dataBuffer.hasRemaining()) {
                            dataBuffer.flip();
                            remoteInputStream.acceptBuffer(dataBuffer);
                            dataBuffer = null;
                            decoderState = DecoderState.NEW_CHUNK;
                        }
                        int n = channel.read(dataBuffer);
                        if (n == -1) {
                            IoUtils.safeClose(channel);
                            return;
                        }
                        if (n == 0) {
                            remoteInputStream.scheduleResumeReads(channel);
                            return;
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                remoteInputStream.acceptException("Read from remote input stream failed: " + e.getMessage());
                IoUtils.safeClose(channel);
            }
        }

        public void handleWritable(final StreamSourceChannel channel) {
        }

        public void handleClosed(final StreamSourceChannel channel) {
            remoteInputStream.acceptEof();
        }
    }

    public static final class RemoteInputStream extends InputStream {

        private enum StreamState {
            RUNNING,
            EOF,
            CLOSED,
        }

        private final IoFuture<StreamSourceChannel> futureChannel;
        private final BufferAllocator<ByteBuffer> allocator;

        private final Object lock = new Object();

        // @protectedby lock
        private StreamState state;
        private ByteBuffer current;
        private ByteBuffer next;
        private String pendingException;
        private boolean pendingResumeReads = false;

        private RemoteInputStream(final IoFuture<StreamSourceChannel> futureChannel, final BufferAllocator<ByteBuffer> allocator) {
            this.futureChannel = futureChannel;
            this.allocator = allocator;
        }

        protected void acceptBuffer(ByteBuffer buffer) {
            synchronized (lock) {
                if (! buffer.hasRemaining()) {
                    throw new IllegalArgumentException("empty buffer");
                }
                if (state == StreamState.CLOSED) {
                    allocator.free(buffer);
                }
                if (current == null) {
                    current = buffer;
                    lock.notifyAll();
                } else if (next == null) {
                    next = buffer;
                } else {
                    throw new IllegalStateException();
                }
            }
        }

        protected void acceptException(String exception) {
            synchronized (lock) {
                pendingException = exception;
                if (current == null) {
                    lock.notifyAll();
                }
            }
        }

        protected void acceptEof() {
            synchronized (lock) {
                if (state == StreamState.RUNNING) {
                    state = StreamState.EOF;
                    if (current == null) {
                        lock.notifyAll();
                    }
                }
            }
        }

        protected void scheduleResumeReads(StreamSourceChannel channel) {
            synchronized (lock) {
                if (state == StreamState.CLOSED || state == StreamState.EOF) {
                    return;
                }
                if (next == null || current == null) {
                    channel.resumeReads();
                } else {
                    pendingResumeReads = true;
                }
            }
        }

        private ByteBuffer getCurrent() throws IOException {
            boolean intr = false;
            try {
                while (current == null) {
                    if (pendingException != null) {
                        throw new IOException(pendingException);
                    } else if (state == StreamState.EOF) {
                        return null;
                    }
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                }
                return current;
            } finally {
                if (intr) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        public int read() throws IOException {
            synchronized (lock) {
                if (state == StreamState.CLOSED) {
                    return -1;
                }
                final ByteBuffer buffer = getCurrent();
                if (buffer == null) {
                    return -1;
                }
                final byte v = buffer.get();
                if (! buffer.hasRemaining()) {
                    current = next;
                    next = null;
                    allocator.free(buffer);
                    if (pendingResumeReads) {
                        futureChannel.get().resumeReads();
                        pendingResumeReads = false;
                    }
                }
                return v & 0xff;
            }
        }

        public int read(final byte b[], final int off, final int len) throws IOException {
            synchronized (lock) {
                if (state == StreamState.CLOSED) {
                    return -1;
                }
                final ByteBuffer buffer = getCurrent();
                if (buffer == null) {
                    return -1;
                }
                final int cnt = Math.min(buffer.remaining(), len);
                buffer.get(b, off, cnt);
                if (! buffer.hasRemaining()) {
                    current = next;
                    next = null;
                    allocator.free(buffer);
                    if (pendingResumeReads) {
                        futureChannel.get().resumeReads();
                        pendingResumeReads = false;
                    }
                }
                return cnt;
            }
        }

        public void close() throws IOException {
            synchronized (lock) {
                if (state != StreamState.CLOSED) {
                    if (current != null) {
                        allocator.free(current);
                        current = null;
                    }
                    if (next != null) {
                        allocator.free(next);
                        next = null;
                    }
                    state = StreamState.CLOSED;
                    futureChannel.get().close();
                }
            }
        }

        public int available() throws IOException {
            synchronized (lock) {
                return current == null ? 0 : current.remaining() + (next == null ? 0 : next.remaining());
            }
        }
    }
}