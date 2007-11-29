package org.jboss.cx.remoting.stream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.jboss.cx.remoting.spi.BufferFactory;

/**
 *
 */
public final class Streams {
    private Streams() {
    }

    private static final ByteBuffer emptyBuffer = ByteBuffer.allocate(0);

    public static InputStream getInputStream(ObjectSource<ByteBuffer> source, boolean propagateClose) {
        return new ByteBufferSourceInputStream(source, propagateClose);
    }

    public static OutputStream getOutputStream(ObjectSink<ByteBuffer> sink, final BufferFactory bufferFactory, final boolean propagateFlush, boolean propagateClose) {
        return new ByteBufferSinkOutputStream(sink, bufferFactory, propagateFlush, propagateClose);
    }

    public static ObjectSource<ByteBuffer> getObjectSource(InputStream stream, final BufferFactory bufferFactory, boolean propagateClose) {
        return new InputStreamByteBufferSource(stream, bufferFactory, propagateClose);
    }

    public static ObjectSink<ByteBuffer> getObjectSink(OutputStream stream, boolean propagateFlush, boolean propagateClose) {
        return new OutputStreamByteBufferSink(stream, propagateFlush, propagateClose);
    }

    public static <T> ObjectSink<T> getCollectionObjectSink(Collection<T> target) {
        return new CollectionObjectSink<T>(target);
    }

    public static <T> ObjectSource<T> getIteratorObjectSource(Iterator<T> iterator) {
        return new IteratorObjectSource<T>(iterator);
    }

    private static final class ByteBufferSourceInputStream extends InputStream {
        private final ObjectSource<ByteBuffer> source;
        private ByteBuffer current = emptyBuffer;
        private final boolean propagateClose;

        public void close() throws IOException {
            if (propagateClose) {
                source.close();
            }
        }

        private ByteBufferSourceInputStream(final ObjectSource<ByteBuffer> source, final boolean propagateClose) {
            this.source = source;
            this.propagateClose = propagateClose;
        }

        public int read() throws IOException {
            if (current == null) {
                return -1;
            }
            while (!current.hasRemaining()) {
                if (!next()) {
                    return -1;
                }
            }
            return current.get() & 0xff;
        }

        public int read(byte b[], int off, int len) throws IOException {
            if (current == null) {
                return -1;
            }
            final int total = len;
            loop:
            while (len > 0) {
                while (!current.hasRemaining()) {
                    if (!next()) {
                        break loop;
                    }
                }
                final int count = Math.min(len, current.remaining());
                current.get(b, off, count);
                len -= count;
                off += count;
            }
            return total - len;
        }

        public long skip(long n) throws IOException {
            final long total = n;
            loop:
            while (n > 0L) {
                while (!current.hasRemaining()) {
                    if (!next()) {
                        break loop;
                    }
                }
                // since remaining is an int, the result will always fit in an int
                final int count = (int) Math.min(n, (long) current.remaining());
                current.position(current.position() + count);
                n -= (long) count;
            }
            return total - n;
        }

        public int available() throws IOException {
            return current == null ? 0 : current.remaining();
        }

        private boolean next() throws IOException {
            if (source.hasNext()) {
                current = source.next();
                return true;
            } else {
                current = null;
                return false;
            }
        }
    }

    private static final class ByteBufferSinkOutputStream extends OutputStream {
        private final ObjectSink<ByteBuffer> sink;
        private final boolean propagateClose;
        private final boolean propagateFlush;
        private final BufferFactory bufferFactory;

        private ByteBuffer buffer;

        public void close() throws IOException {
            if (propagateClose) {
                sink.close();
            }
        }

        private ByteBufferSinkOutputStream(final ObjectSink<ByteBuffer> sink, final BufferFactory bufferFactory, final boolean propagateFlush, final boolean propagateClose) {
            this.sink = sink;
            this.bufferFactory = bufferFactory;
            this.propagateClose = propagateClose;
            this.propagateFlush = propagateFlush;
        }

        public void write(int b) throws IOException {
            if (buffer == null) {
                buffer = bufferFactory.create();
            }
            buffer.put((byte) b);
            if (!buffer.hasRemaining()) {
                doFlush();
            }
        }

        public void write(byte b[], int off, int len) throws IOException {
            while (len > 0) {
                if (buffer == null) {
                    buffer = bufferFactory.create();
                }
                final int count = Math.min(buffer.remaining(), len);
                buffer.put(b, off, count);
                len -= count;
                off += count;
                if (!buffer.hasRemaining()) {
                    doFlush();
                }
            }
        }

        public void flush() throws IOException {
            if (buffer == null) {
                buffer = bufferFactory.create();
            }
            if (buffer.position() > 0) {
                doFlush();
            }
            if (propagateFlush) {
                sink.flush();
            }
        }

        private void doFlush() throws IOException {
            buffer.flip();
            try {
                sink.accept(buffer);
            } finally {
                buffer = null;
            }
        }
    }

    private static final class InputStreamByteBufferSource implements ObjectSource<ByteBuffer> {
        private final InputStream stream;
        private final boolean propagateClose;
        private final BufferFactory bufferFactory;

        private ByteBuffer buffer;

        public InputStreamByteBufferSource(final InputStream stream, final BufferFactory bufferFactory, final boolean propagateClose) {
            this.stream = stream;
            this.bufferFactory = bufferFactory;
            this.propagateClose = propagateClose;
        }

        public boolean hasNext() throws IOException {
            return buffer != null && buffer.hasRemaining() || populate();
        }

        public ByteBuffer next() throws IOException {
            if (buffer == null || !buffer.hasRemaining()) {
                if (!populate()) {
                    throw new EOFException("End of file reached");
                }
            }
            try {
                return buffer;
            } finally {
                buffer = null;
            }
        }

        private boolean populate() throws IOException {
            if (buffer == null) {
                buffer = bufferFactory.create();
            }
            int count;
            int pos = buffer.position();
            int rem = buffer.remaining();
            while (rem > 0 && (count = stream.read(buffer.array(), pos, rem)) > 0) {
                pos += count;
                rem -= count;
            }
            buffer.position(pos);
            return buffer.hasRemaining();
        }

        public void close() throws IOException {
            if (propagateClose) {
                stream.close();
            }
        }
    }

    private static final class OutputStreamByteBufferSink implements ObjectSink<ByteBuffer> {
        private final OutputStream stream;
        private final boolean propagateClose;
        private final boolean propagateFlush;
        private byte[] tempStore;

        public OutputStreamByteBufferSink(final OutputStream stream, final boolean propagateFlush, final boolean propagateClose) {
            this.stream = stream;
            this.propagateClose = propagateClose;
            this.propagateFlush = propagateFlush;
        }

        public void accept(final ByteBuffer buffer) throws IOException {
            if (!buffer.hasRemaining()) {
                return;
            }
            if (buffer.hasArray()) {
                // Optimization: we can write data directly from the buffer array, avoid a copy
                final byte[] bytes = buffer.array();
                final int offs = buffer.arrayOffset();
                final int len = buffer.remaining();
                stream.write(bytes, offs, len);
                buffer.clear();
            } else {
                // gotta copy the data out a bit at a time
                final byte[] tempStore;
                if (this.tempStore == null) {
                    tempStore = this.tempStore = new byte[1024];
                } else {
                    tempStore = this.tempStore;
                }
                while (buffer.hasRemaining()) {
                    int count = Math.min(buffer.remaining(), tempStore.length);
                    buffer.get(tempStore, 0, count);
                    stream.write(tempStore, 0, count);
                }
            }
        }

        public void flush() throws IOException {
            if (propagateFlush) {
                stream.flush();
            }
        }

        public void close() throws IOException {
            if (propagateClose) {
                stream.close();
            }
        }
    }

    private static final class CollectionObjectSink<T> implements ObjectSink<T> {
        private final Collection<T> target;

        public CollectionObjectSink(final Collection<T> target) {
            this.target = target;
        }

        public void accept(final T instance) throws IOException {
            target.add(instance);
        }

        public void flush() throws IOException {
        }

        public void close() throws IOException {
        }
    }

    private static final class IteratorObjectSource<T> implements ObjectSource<T> {
        private final Iterator<T> src;

        public IteratorObjectSource(final Iterator<T> src) {
            this.src = src;
        }

        public boolean hasNext() throws IOException {
            return src.hasNext();
        }

        public T next() throws IOException {
            try {
                return src.next();
            } catch (NoSuchElementException ex) {
                EOFException eex = new EOFException("Iteration past end of iterator");
                eex.setStackTrace(ex.getStackTrace());
                throw eex;
            }
        }

        public void close() throws IOException {
            //empty
        }
    }
}
