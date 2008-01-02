package org.jboss.cx.remoting.core.stream;

import java.io.InputStream;
import java.io.IOException;
import java.util.Queue;
import java.util.LinkedList;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.spi.stream.StreamContext;
import org.jboss.cx.remoting.spi.stream.StreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.spi.stream.RemoteStreamSerializer;
import org.jboss.cx.remoting.spi.protocol.MessageInput;

/**
 *
 */
public final class InputStreamStreamSerializerFactory implements StreamSerializerFactory {
    public InputStreamStreamSerializerFactory() {
        // no-arg constructor required
    }

    public StreamSerializer getLocalSide(StreamContext context, Object local) throws IOException {
        return new StreamSerializerImpl(context, (InputStream)local);
    }

    public RemoteStreamSerializer getRemoteSide(final StreamContext context) throws IOException {
        return new RemoteStreamSerializerImpl(context);
    }

    private final static class StreamSerializerImpl implements StreamSerializer {
        private final StreamContext context;
        private final InputStream inputStream;

        public StreamSerializerImpl(final StreamContext context, final InputStream inputStream) {
            this.context = context;
            this.inputStream = inputStream;
        }

        public void handleData(MessageInput data) throws IOException {
        }

        public void handleClose() throws IOException {
            
        }
    }

    private enum Type {
        DATA,
        END,
        FAILURE,
    }

    private final static class RemoteStreamSerializerImpl implements RemoteStreamSerializer {

        private final StreamContext context;
        private Queue<Entry> messageQueue = CollectionUtil.synchronizedQueue(new LinkedList<Entry>());

        private final class Entry {
            private final Type type;
            private final MessageInput msg;
            private final Throwable t;

            public Entry(final MessageInput msg) {
                this.msg = msg;
                type = Type.DATA;
                t = null;
            }

            public Entry(final Throwable t) {
                type = Type.FAILURE;
                this.t = t;
                msg = null;
            }

            public Entry() {
                type = Type.END;
                msg = null;
                t = null;
            }
        }

        public RemoteStreamSerializerImpl(final StreamContext context) {
            this.context = context;
        }

        public InputStream getRemoteInstance() {
            return new InputStream() {

                public int read() throws IOException {
                    synchronized(messageQueue) {
                        Entry e = getHead();
                        if (e.type == Type.FAILURE) {
                            try {
                                throw e.t;
                            } catch (IOException ex) {
                                throw ex;
                            } catch (Throwable ex) {
                                throw new RuntimeException("A remote exception was thrown: " + ex.toString(), ex);
                            }
                        } else if (e.type == Type.END) {
                            return -1;
                        }
                        final int v = e.msg.read();
                        if (v == -1) {
                            messageQueue.remove();
                            return read();
                        }
                        return v;
                    }
                }
            };
        }

        // Call with messageQueue locked only!
        private Entry getHead() {
            RemoteStreamSerializerImpl.Entry head;
            boolean intr = false;
            try {
                for (;;) {
                    head = messageQueue.peek();
                    if (head != null) {
                        return head;
                    }
                    try {

                        messageQueue.wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                }
            } finally {
                if (intr) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        public void sendMoreRequest() throws IOException {

        }


        public void handleData(MessageInput data) throws IOException {

        }

        public void handleClose() throws IOException {
        }

    }
}
