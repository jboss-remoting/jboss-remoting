package org.jboss.remoting.core.stream;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import org.jboss.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.remoting.spi.stream.StreamContext;
import org.jboss.remoting.stream.ObjectSource;
import org.jboss.xnio.channels.AllocatedMessageChannel;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.BufferAllocator;
import static org.jboss.xnio.Buffers.flip;
import org.jboss.xnio.log.Logger;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.ByteOutput;

/**
 *
 */
public final class ObjectSourceStreamSerializerFactory implements StreamSerializerFactory {

    private static final long serialVersionUID = -7485283009011459281L;

    private static final Logger log = Logger.getLogger(ObjectSourceStreamSerializerFactory.class);

    private MarshallerFactory marshallerFactory;

    public MarshallerFactory getMarshallerFactory() {
        return marshallerFactory;
    }

    public void setMarshallerFactory(final MarshallerFactory marshallerFactory) {
        this.marshallerFactory = marshallerFactory;
    }

    public IoHandler<? super AllocatedMessageChannel> getLocalSide(final Object localSide, final StreamContext streamContext) throws IOException {
        return null;
    }

    public Object getRemoteSide(final ChannelSource<AllocatedMessageChannel> channelSource, final StreamContext streamContext) throws IOException {
        return null;
    }

    public static class LocalHandler implements IoHandler<AllocatedMessageChannel> {
        private final ObjectSource objectSource;
        private final Object lock = new Object();
        private final Executor executor;
        private final Marshaller marshaller;
        private ByteBuffer[] current;
        private final Runnable fillTask = new FillTask();
        private final BufferAllocator<ByteBuffer> allocator;

        public LocalHandler(final ObjectSource source, final Executor executor, final Marshaller marshaller, final BufferAllocator<ByteBuffer> allocator) {
            objectSource = source;
            this.executor = executor;
            this.marshaller = marshaller;
            this.allocator = allocator;
        }

        public void handleOpened(final AllocatedMessageChannel channel) {
            executor.execute(fillTask);
        }

        public void handleReadable(final AllocatedMessageChannel channel) {
            // not invoked
        }

        public void handleWritable(final AllocatedMessageChannel channel) {
        }

        public void handleClosed(final AllocatedMessageChannel channel) {
            IoUtils.safeClose(objectSource);
        }

        public class FillTask implements Runnable {
            public void run() {
                try {
                    if (objectSource.hasNext()) {
                        final BufferProducingByteOutput output = new BufferProducingByteOutput(allocator);
                        try {
                            marshaller.start(output);
                            marshaller.writeObject(objectSource.next());
                            marshaller.finish();
                            output.flush();
                            final ByteBuffer[] buffers = output.takeBuffers();
                            
                        } finally {
                            IoUtils.safeClose(output);
                        }

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class BufferProducingByteOutput implements ByteOutput {

        private final BufferAllocator<ByteBuffer> allocator;
        private final List<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
        private ByteBuffer current;

        public BufferProducingByteOutput(final BufferAllocator<ByteBuffer> allocator) {
            this.allocator = allocator;
        }

        public void write(final int i) throws IOException {
            ByteBuffer buffer = current;
            if (buffer == null) {
                buffer = (current = allocator.allocate());
            }
            buffer.put((byte) i);
            if (! buffer.hasRemaining()) {
                buffers.add(flip(buffer));
                current = null;
            }
        }

        public void write(final byte[] bytes) throws IOException {
            write(bytes, 0, bytes.length);
        }

        public void write(final byte[] bytes, int offs, int len) throws IOException {
            while (len > 0) {
                ByteBuffer buffer = current;
                if (buffer == null) {
                    buffer = (current = allocator.allocate());
                }
                final int rem = Math.min(buffer.remaining(), len);
                buffer.put(bytes, offs, rem);
                offs += rem;
                len -= rem;
                if (! buffer.hasRemaining()) {
                    buffers.add(flip(buffer));
                    current = null;
                }
            }
        }

        public void close() throws IOException {
            flush();
        }

        public void flush() throws IOException {
            final ByteBuffer buffer = current;
            if (buffer != null) {
                buffers.add(buffer);
                current = null;
            }
        }

        public ByteBuffer[] takeBuffers() {
            try {
                return buffers.toArray(new ByteBuffer[buffers.size()]);
            } finally {
                buffers.clear();
            }
        }
    }

    public static class RemoteHandler implements IoHandler<AllocatedMessageChannel> {

        public void handleOpened(final AllocatedMessageChannel channel) {
        }

        public void handleReadable(final AllocatedMessageChannel channel) {
        }

        public void handleWritable(final AllocatedMessageChannel channel) {
        }

        public void handleClosed(final AllocatedMessageChannel channel) {
        }
    }

    public static class RemoteObjectSource implements ObjectSource {

        public boolean hasNext() throws IOException {
            return false;
        }

        public Object next() throws IOException {
            return null;
        }

        public void close() throws IOException {
        }
    }
}
