package org.jboss.cx.remoting.core.stream;

import java.io.IOException;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;
import org.jboss.cx.remoting.util.MessageInput;
import org.jboss.cx.remoting.util.MessageOutput;
import org.jboss.cx.remoting.spi.stream.RemoteStreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamContext;
import org.jboss.cx.remoting.spi.stream.StreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.stream.ObjectSource;

/**
 *
 */
public final class ObjectSourceStreamSerializerFactory implements StreamSerializerFactory {
    public StreamSerializer getLocalSide(final StreamContext context, final Object local) throws IOException {
        return new StreamSerializerImpl(context, (ObjectSource<?>) local);
    }

    public RemoteStreamSerializer getRemoteSide(final StreamContext context) throws IOException {
        return new RemoteStreamSerializerImpl(context);
    }

    private static final class StreamSerializerImpl implements StreamSerializer {
        private final StreamContext streamContext;
        private final ObjectSource<?> objectSource;

        public StreamSerializerImpl(final StreamContext streamContext, final ObjectSource<?> objectSource) throws IOException {
            this.streamContext = streamContext;
            this.objectSource = objectSource;
        }

        public void handleOpen() throws IOException {
            transmitNext();
        }

        public void handleData(MessageInput data) throws IOException {
            transmitNext();
        }

        public void handleClose() throws IOException {
            objectSource.close();
        }

        private void transmitNext() throws IOException {
            final MessageOutput msg = streamContext.writeMessage();
            final boolean hasNext = objectSource.hasNext();
            msg.writeBoolean(hasNext);
            if (hasNext) {
                msg.writeObject(objectSource.next());
                msg.writeBoolean(objectSource.hasNext());
            }
            msg.commit();
            msg.close();
        }

    }

    private static final class RemoteStreamSerializerImpl implements RemoteStreamSerializer {

        private enum Type {
            ITEM,
            EXCEPTION,
            CLOSE,
            END,
        }

        private class Message {
            private final Type type;
            private final Object data;

            public Message(final Type type, final Object data) {
                this.type = type;
                this.data = data;
            }
        }

        private final Queue<Message> messageQueue = new LinkedList<Message>();

        private final StreamContext context;

        public RemoteStreamSerializerImpl(final StreamContext context) {
            this.context = context;
        }

        public ObjectSource getRemoteInstance() {
            return new ObjectSource() {
                public boolean hasNext() throws IOException {
                    boolean intr = Thread.interrupted();
                    try {
                        synchronized(messageQueue) {
                            while (messageQueue.isEmpty()) {
                                try {
                                    messageQueue.wait();
                                } catch (InterruptedException e) {
                                    intr = true;
                                    Thread.interrupted();
                                }
                            }
                            final Message msg = messageQueue.peek();
                            return msg.type != Type.END;
                        }
                    } finally {
                        if (intr) Thread.currentThread().interrupt();
                    }
                }

                public Object next() throws IOException {
                    boolean intr = Thread.interrupted();
                    try {
                        synchronized(messageQueue) {
                            while (messageQueue.isEmpty()) {
                                try {
                                    messageQueue.wait();
                                } catch (InterruptedException e) {
                                    intr = true;
                                    Thread.interrupted();
                                }
                            }
                            final Message msg = messageQueue.remove();
                            final MessageOutput omsg;
                            switch (msg.type) {
                                case ITEM:
                                    omsg = context.writeMessage();
                                    omsg.commit();
                                    omsg.close();
                                    return msg.data;
                                case EXCEPTION:
                                    omsg = context.writeMessage();
                                    omsg.commit();
                                    omsg.close();
                                    throw (IOException) msg.data;
                                case END:
                                    messageQueue.add(msg);
                                    throw new NoSuchElementException("next() past end of iterator");
                                case CLOSE:
                                    messageQueue.add(msg);
                                    throw new IOException("Channel closed");
                            }
                            throw new IllegalStateException("wrong state");
                        }
                    } finally {
                        if (intr) Thread.currentThread().interrupt();
                    }
                }

                public void close() throws IOException {
                    context.close();
                    synchronized(messageQueue) {
                        messageQueue.clear();
                        messageQueue.add(new Message(Type.CLOSE, null));
                    }
                }
            };
        }

        public void handleOpen() throws IOException {
        }

        @SuppressWarnings ({"unchecked"})
        public void handleData(MessageInput data) throws IOException {
            synchronized(messageQueue) {
                if (! data.readBoolean()) {
                    messageQueue.add(new Message(Type.END, null));
                } else {
                    final Object obj;
                    try {
                        obj = data.readObject();
                        messageQueue.add(new Message(Type.ITEM, obj));
                    } catch (ClassNotFoundException e) {
                        messageQueue.add(new Message(Type.EXCEPTION, new IOException("Failed to load class for next item: " + e.toString())));
                    }
                    if (! data.readBoolean()) {
                        messageQueue.add(new Message(Type.END, null));
                    }
                }
            }
        }

        public void handleClose() throws IOException {
            synchronized(messageQueue) {
                messageQueue.add(new Message(Type.CLOSE, null));
            }
        }

    }
}
