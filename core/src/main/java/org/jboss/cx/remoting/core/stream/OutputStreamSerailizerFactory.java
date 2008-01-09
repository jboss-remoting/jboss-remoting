package org.jboss.cx.remoting.core.stream;

import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.spi.stream.StreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamContext;
import org.jboss.cx.remoting.spi.stream.RemoteStreamSerializer;
import org.jboss.cx.remoting.spi.protocol.MessageInput;
import org.jboss.cx.remoting.spi.protocol.MessageOutput;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Stream serializer for {@link java.io.OutputStream} instances.
 */
public final class OutputStreamSerailizerFactory implements StreamSerializerFactory {
    public StreamSerializer getLocalSide(StreamContext context, Object local) throws IOException {
        return new StreamSerializerImpl((OutputStream)local);
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

    private static final class StreamSerializerImpl implements StreamSerializer {
        private final OutputStream outputStream;

        public StreamSerializerImpl(final OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        public void handleOpen() throws IOException {
        }

        public void handleData(MessageInput data) throws IOException {
            MessageType messageType = MessageType.values()[data.read()];
            switch (messageType) {
                case DATA:
                    for (int i = data.read(); i != -1; i = data.read()) {
                        outputStream.write(i);
                    }
                    break;
                case FLUSH:
                    outputStream.flush();
                    break;
            }
        }

        public void handleClose() throws IOException {
            // Don't automatically close.
            outputStream.flush();
        }
    }

    private static final int BUFFER_SIZE = 4096;

    private static final class RemoteStreamSerializerImpl implements RemoteStreamSerializer {
        private final StreamContext context;
        private final Object monitor = new Object();
        private MessageOutput current;

        public RemoteStreamSerializerImpl(final StreamContext context) {
            this.context = context;
        }

        public OutputStream getRemoteInstance() {
            return new OutputStream() {
                public void write(int b) throws IOException {
                    synchronized(monitor) {
                        if (current == null) {
                            current = context.writeMessage();
                        }
                        current.write(b);
                        if (current.getBytesWritten() > BUFFER_SIZE) {
                            flush();
                        }
                    }
                }

                public void write(byte b[], int off, int len) throws IOException {
                    synchronized(monitor) {
                        if (current == null) {
                            current = context.writeMessage();
                        }
                        current.write(b, off, len);
                        if (current.getBytesWritten() > BUFFER_SIZE) {
                            flush();
                        }
                    }
                }

                public void flush() throws IOException {
                    synchronized(monitor) {
                        if (current != null) {
                            current.commit();
                            current = null;
                        }
                    }
                }

                public void close() throws IOException {
                    synchronized(monitor) {
                        context.close();
                    }
                }
            };
        }

        public void handleOpen() throws IOException {
        }

        public void handleData(MessageInput data) throws IOException {
            // ignore
        }

        public void handleClose() throws IOException {
            // ignore
        }
    }
}
