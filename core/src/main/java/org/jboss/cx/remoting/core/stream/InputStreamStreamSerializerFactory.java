package org.jboss.cx.remoting.core.stream;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import org.jboss.cx.remoting.util.MessageInput;
import org.jboss.cx.remoting.util.MessageOutput;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.spi.stream.RemoteStreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamContext;
import org.jboss.cx.remoting.spi.stream.StreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;

/**
 *
 */
public final class InputStreamStreamSerializerFactory implements StreamSerializerFactory {
    private static final Logger log = Logger.getLogger(InputStreamStreamSerializerFactory.class);

    public InputStreamStreamSerializerFactory() {
        // no-arg constructor required
    }

    public StreamSerializer getLocalSide(StreamContext context, Object local) throws IOException {
        return new StreamSerializerImpl(context, (InputStream)local);
    }

    public RemoteStreamSerializer getRemoteSide(final StreamContext context) throws IOException {
        return new RemoteStreamSerializerImpl(context);
    }

    private enum Type {
        DATA,
        END,
    }

    private static final int BUF_LEN = 512;

    private final static class StreamSerializerImpl implements StreamSerializer {
        private final StreamContext context;
        private final InputStream inputStream;

        public StreamSerializerImpl(final StreamContext context, final InputStream inputStream) throws IOException {
            this.context = context;
            this.inputStream = inputStream;
        }

        public void handleOpen() throws IOException {
            sendNext();
        }

        public void handleData(MessageInput data) throws IOException {
            sendNext();
        }

        private void sendNext() throws IOException {
            final MessageOutput output = context.writeMessage();
            final byte[] bytes = new byte[BUF_LEN];
            int i, t;
            boolean end = false;
            for (t = 0; t < BUF_LEN; t += i) {
                i = inputStream.read(bytes);
                if (i == -1) {
                    end = true;
                    break;
                }
            }
            if (t > 0) {
                log.trace("Sending DATA message, %d bytes", t);
                output.write(Type.DATA.ordinal());
                output.writeInt(t);
                output.write(bytes, 0, t);
            }
            if (end) {
                log.trace("Sending END message");
                output.write(Type.END.ordinal());
            }
            output.commit();
        }

        public void handleClose() throws IOException {
        }
    }

    private final static class RemoteStreamSerializerImpl implements RemoteStreamSerializer {

        private final StreamContext context;
        private LinkedList<Entry> messageQueue = new LinkedList<Entry>();

        private final class Entry {
            private final Type type;
            private final byte[] msg;
            private int i;

            public Entry(final byte[] msg) {
                this.msg = msg;
                type = Type.DATA;
            }

            public Entry() {
                type = Type.END;
                msg = null;
            }
        }

        public RemoteStreamSerializerImpl(final StreamContext context) {
            this.context = context;
        }

        public InputStream getRemoteInstance() {
            return new InputStream() {

                public int read() throws IOException {
                    boolean intr = Thread.interrupted();
                    try {
                        synchronized(messageQueue) {
                            for (;;) {
                                if (messageQueue.size() == 0) {
                                    context.writeMessage().commit();
                                    do {
                                        try {
                                            messageQueue.wait();
                                        } catch (InterruptedException e) {
                                            intr = true;
                                        }
                                    } while (messageQueue.size() == 0);
                                }
                                final RemoteStreamSerializerImpl.Entry first = messageQueue.getFirst();
                                switch (first.type) {
                                    case DATA:
                                        if (first.msg.length <= first.i) {
                                            messageQueue.removeFirst();
                                        } else {
                                            return first.msg[first.i ++] & 0xff;
                                        }
                                    default:
                                        return -1;
                                }
                            }
                        }
                    } finally {
                        if (intr) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            };
        }

        public void handleOpen() throws IOException {
        }

        public void handleData(MessageInput data) throws IOException {
            synchronized(messageQueue) {
                for (;;) {
                    final int d = data.read();
                    if (d == -1) {
                        break;
                    }
                    Type t = Type.values()[d];
                    switch (t) {
                        case DATA:
                            int l = data.readInt();
                            byte[] bytes = new byte[l];
                            data.read(bytes);
                            log.trace("Received DATA message; %d bytes", bytes.length);
                            messageQueue.add(new Entry(bytes));
                            break;
                        case END:
                            log.trace("Received END message");
                            messageQueue.add(new Entry());
                            break;
                    }
                }
                messageQueue.notifyAll();
            }
        }

        public void handleClose() throws IOException {
            synchronized(messageQueue) {
                messageQueue.add(new Entry());
            }
        }
    }
}
