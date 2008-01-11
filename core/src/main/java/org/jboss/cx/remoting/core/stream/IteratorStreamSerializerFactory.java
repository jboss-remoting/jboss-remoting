package org.jboss.cx.remoting.core.stream;

import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.spi.stream.StreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamContext;
import org.jboss.cx.remoting.spi.stream.RemoteStreamSerializer;
import org.jboss.cx.remoting.spi.protocol.MessageInput;
import org.jboss.cx.remoting.stream.Streams;
import org.jboss.cx.remoting.stream.ObjectSource;
import java.io.IOException;
import java.util.Iterator;

/**
 *
 */
public final class IteratorStreamSerializerFactory implements StreamSerializerFactory {
    private final ObjectSourceStreamSerializerFactory other = new ObjectSourceStreamSerializerFactory();

    public StreamSerializer getLocalSide(StreamContext context, Object local) throws IOException {
        return other.getLocalSide(context, Streams.getIteratorObjectSource((Iterator<?>)local));
    }

    public RemoteStreamSerializer getRemoteSide(StreamContext context) throws IOException {
        return new RemoteStreamSerializerImpl(other.getRemoteSide(context), context);
    }

    public static final class RemoteStreamSerializerImpl implements RemoteStreamSerializer {
        private final RemoteStreamSerializer other;
        private final StreamContext context;

        public RemoteStreamSerializerImpl(final RemoteStreamSerializer other, final StreamContext context) {
            this.other = other;
            this.context = context;
        }

        public Iterator<Object> getRemoteInstance() {
            final ObjectSource<?> objectSource = (ObjectSource<?>) other.getRemoteInstance();
            return new Iterator<Object>() {
                public boolean hasNext() {
                    try {
                        return objectSource.hasNext();
                    } catch (IOException e) {
                        throw new IllegalStateException("Illegal state: " + e.toString());
                    }
                }

                public Object next() {
                    try {
                        return objectSource.next();
                    } catch (IOException e) {
                        throw new IllegalStateException("Illegal state: " + e.toString());
                    }
                }

                public void remove() {
                    throw new UnsupportedOperationException("remove()");
                }
            };
        }

        public void handleOpen() throws IOException {
        }

        public void handleData(MessageInput data) throws IOException {
        }

        public void handleClose() throws IOException {
        }
    }
}
