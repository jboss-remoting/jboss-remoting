package org.jboss.cx.remoting.jrpp.mina;

import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.WriteRequest;
import org.apache.mina.common.AttributeKey;
import org.apache.mina.common.IoBuffer;
import org.apache.mina.common.IoBufferWrapper;
import org.apache.mina.common.WriteRequestWrapper;

/**
 *
 */
public final class FramingIoFilter extends IoFilterAdapter {
    private static final AttributeKey FRAMER_KEY = new AttributeKey(FramingIoFilter.class, "framerKey");

    private static final class Framer {
        private final IoSession session;
        private State state = State.INITIAL;
        private final IoBuffer sizeBuf = IoBuffer.allocate(4);
        private IoBuffer target;

        public Framer(final IoSession session) {
            this.session = session;
        }

        public void messageReceived(NextFilter nextFilter, IoBuffer buffer) {
            while (buffer.hasRemaining()) {
                final int r = buffer.remaining();
                switch (state) {
                    case INITIAL:
                        if (r >= 4) {
                            target = IoBuffer.allocate(buffer.getInt());
                            state = State.READING;
                            break;
                        } else {
                            sizeBuf.clear();
                            sizeBuf.put(buffer);
                            state = State.PART_CNT;
                            // buffer MUST be empty now
                            return;
                        }
                    case PART_CNT:
                        while (buffer.hasRemaining() && sizeBuf.hasRemaining()) {
                            sizeBuf.put(buffer.get());
                        }
                        if (sizeBuf.hasRemaining()) {
                            return;
                        }
                        sizeBuf.flip();
                        target = IoBuffer.allocate(sizeBuf.getInt());
                        state = State.READING;
                        break;
                    case READING:
                        if (target.remaining() > buffer.remaining()) {
                            target.put(buffer);
                            return;
                        } else {
                            target.put(consume(buffer, target.remaining()));
                            nextFilter.messageReceived(session, target);
                            state = State.INITIAL;
                            break;
                        }
                }
            }
        }

        private IoBuffer consume(IoBuffer buffer, int cnt) {
            final IoBuffer slice = buffer.duplicate().limit(buffer.limit() - cnt);
            buffer.position(buffer.position() + cnt);
            return slice;
        }

        private enum State {
            INITIAL,
            PART_CNT,
            READING
        }
    }

    private Framer getFramer(IoSession session) {
        return (Framer) session.getAttribute(FRAMER_KEY);
    }

    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
        getFramer(session).messageReceived(nextFilter, (IoBuffer) message);
    }

    public void messageSent(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
        if (writeRequest instanceof FramingWriteRequest) {
            nextFilter.messageSent(session, ((FramingWriteRequest)writeRequest).getOriginalRequest());
        } else {
            nextFilter.messageSent(session, writeRequest);
        }
    }

    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
        final IoBuffer orig = (IoBuffer) writeRequest.getMessage();
        final int pos = orig.position();
        final int osize = orig.remaining();
        final FramedIoBuffer buffer;
        if (pos > 4) {
            // There's room at the start of the buffer for the length field
            buffer = new FramedIoBuffer(orig.duplicate());
            final int newpos = pos - 4;
            buffer.putInt(newpos, buffer.remaining()).position(newpos);
        } else {
            // No space, we have to copy the buffer. :-(
            buffer = new FramedIoBuffer(IoBuffer.allocate(osize + 4));
            buffer.putInt(osize).put(orig.duplicate()).flip();
        }
        nextFilter.filterWrite(session, new FramingWriteRequest(writeRequest, buffer));
    }

    private static final class FramedIoBuffer extends IoBufferWrapper {
        public FramedIoBuffer(IoBuffer buf) {
            super(buf);
        }
    }

    private static final class FramingWriteRequest extends WriteRequestWrapper {
        private final IoBuffer message;

        public FramingWriteRequest(WriteRequest parentRequest, IoBuffer message) {
            super(parentRequest);
            this.message = message;
        }

        public IoBuffer getMessage() {
            return message;
        }
    }
}
