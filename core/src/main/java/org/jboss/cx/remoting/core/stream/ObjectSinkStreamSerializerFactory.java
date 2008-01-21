package org.jboss.cx.remoting.core.stream;

import java.io.IOException;
import org.jboss.cx.remoting.core.util.MessageInput;
import org.jboss.cx.remoting.core.util.MessageOutput;
import org.jboss.cx.remoting.spi.stream.RemoteStreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamContext;
import org.jboss.cx.remoting.spi.stream.StreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.stream.ObjectSink;

/**
 *
 */
public final class ObjectSinkStreamSerializerFactory implements StreamSerializerFactory {
    public StreamSerializer getLocalSide(StreamContext context, Object local) throws IOException {
        return new StreamSerializerImpl(context, (ObjectSink<?>)local);
    }

    public RemoteStreamSerializer getRemoteSide(StreamContext context) throws IOException {
        return new RemoteStreamSerializerImpl(context);
    }

    /**
     * KEEP IN ORDER.
     */
    private enum MessageType {
        DATA,
        FLUSH,
    }

    public static final class StreamSerializerImpl implements StreamSerializer {
        private final StreamContext context;
        private final ObjectSink<?> objectSink;

        public StreamSerializerImpl(final StreamContext context, final ObjectSink<?> objectSink) {
            this.context = context;
            this.objectSink = objectSink;
        }

        public void handleOpen() throws IOException {
        }

        @SuppressWarnings ({"unchecked"})
        public void handleData(MessageInput data) throws IOException {
            MessageType messageType = MessageType.values()[data.read()];
            switch (messageType) {
                case DATA:
                    try {
                        ((ObjectSink)objectSink).accept(data.readObject());
                    } catch (ClassNotFoundException e) {
                        throw new IOException("Cannot deserialize object from message (class not found): " + e.toString());
                    }
                    break;
                case FLUSH:
                    objectSink.flush();
                    break;
            }
        }

        public void handleClose() throws IOException {
            objectSink.flush();
        }
    }

    public static final class RemoteStreamSerializerImpl implements RemoteStreamSerializer {
        private final StreamContext context;

        public RemoteStreamSerializerImpl(final StreamContext context) {
            this.context = context;
        }

        public ObjectSink<?> getRemoteInstance() {
            return new ObjectSink<Object>() {
                public void accept(final Object instance) throws IOException {
                    final MessageOutput msg = context.writeMessage();
                    msg.write(MessageType.DATA.ordinal());
                    msg.writeObject(instance);
                    msg.commit();
                }

                public void flush() throws IOException {
                    final MessageOutput msg = context.writeMessage();
                    msg.write(MessageType.FLUSH.ordinal());
                    msg.commit();
                }

                public void close() throws IOException {
                    flush();
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
