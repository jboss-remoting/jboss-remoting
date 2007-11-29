package org.jboss.cx.remoting.spi;

import java.nio.ByteBuffer;

/**
 *
 */
public abstract class BufferFactory {
    protected final int size;
    protected final boolean direct;

    protected BufferFactory(final int size, final boolean direct) {
        this.size = size;
        this.direct = direct;
    }

    public abstract ByteBuffer create();

    public final int getSize() {
        return size;
    }

    public final boolean isDirect() {
        return direct;
    }

    public static BufferFactory create(int size, boolean direct) throws IllegalArgumentException {
        if (size < 0) {
            throw new IllegalArgumentException("Negative buffer size given");
        }
        if (direct) {
            return new DirectFactoryImpl(size);
        } else {
            return new HeapFactoryImpl(size);
        }
    }

    private static final class HeapFactoryImpl extends BufferFactory {

        private HeapFactoryImpl(final int size) {
            super(size, false);
        }

        public ByteBuffer create() {
            return ByteBuffer.allocate(size);
        }
    }

    private static final class DirectFactoryImpl extends BufferFactory {

        private DirectFactoryImpl(final int size) {
            super(size, true);
        }

        public ByteBuffer create() {
            return ByteBuffer.allocateDirect(size);
        }
    }
}
