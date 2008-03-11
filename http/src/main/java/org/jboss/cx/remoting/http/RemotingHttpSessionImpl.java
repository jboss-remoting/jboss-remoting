package org.jboss.cx.remoting.http;

import org.jboss.cx.remoting.http.spi.RemotingHttpSessionContext;
import org.jboss.cx.remoting.http.spi.IncomingHttpMessage;
import org.jboss.cx.remoting.http.spi.ReadyNotifier;
import org.jboss.cx.remoting.http.spi.OutgoingHttpMessage;
import org.jboss.cx.remoting.http.spi.AbstractOutgoingHttpMessage;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.spi.ObjectMessageOutput;
import org.jboss.cx.remoting.spi.ByteMessageOutput;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.RemoteExecutionException;

import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 *
 */
public final class RemotingHttpSessionImpl {
    private final RemotingHttpSessionContext context = new SessionContext();
    private final ProtocolContext protocolContext;
    private final ProtocolHandler protocolHandler = new ProtocolHandlerImpl();
    private final BlockingQueue<IncomingHttpMessage> incomingQueue = CollectionUtil.synchronizedQueue(new LinkedList<IncomingHttpMessage>());
    private final BlockingQueue<OutputAction> outgoingQueue = CollectionUtil.synchronizedQueue(new LinkedList<OutputAction>());
    private final String sessionId;
    private final AtomicLong outputSequence = new AtomicLong(0L);
    private final AtomicLong inputSequence = new AtomicLong(0L);

    private static final int PROTOCOL_VERSION = 0;

    public RemotingHttpSessionImpl(final HttpProtocolSupport protocolSupport, final ProtocolContext protocolContext) {
        this.protocolContext = protocolContext;
        String sessionId;
        do {
            sessionId = protocolSupport.generateSessionId();
        } while (! protocolSupport.registerSession(sessionId, context));
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public RemotingHttpSessionContext getContext() {
        return context;
    }

    public ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    private final class SessionContext implements RemotingHttpSessionContext {
        private final Set<ReadyNotifier> readyNotifiers = CollectionUtil.synchronizedSet(new HashSet<ReadyNotifier>());

        public void queueMessage(IncomingHttpMessage message) {
            incomingQueue.add(message);
            synchronized(readyNotifiers) {
                for (ReadyNotifier notifier : readyNotifiers) {
                    notifier.notifyReady(this);
                }
            }
        }

        public void addReadyNotifier(ReadyNotifier notifier) {
            readyNotifiers.add(notifier);
        }

        public OutgoingHttpMessage getNextMessageImmediate() {
            final List<OutputAction> actions = CollectionUtil.arrayList();
            outgoingQueue.drainTo(actions);
            if (actions.isEmpty()) {
                return null;
            }
            return new OutgoingActionHttpMessage(actions);
        }

        public OutgoingHttpMessage getNextMessage(long timeoutMillis) throws InterruptedException {
            synchronized(outgoingQueue) {
                final OutputAction first = outgoingQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
                if (first != null) {
                    final List<OutputAction> actions = CollectionUtil.arrayList();
                    actions.add(first);
                    outgoingQueue.drainTo(actions);
                    return new OutgoingActionHttpMessage(actions);
                } else {
                    return null;
                }
            }
        }
    }

    private void write(ObjectOutput output, MsgType type) throws IOException {
        output.writeInt(type.ordinal());
    }

    private void write(ObjectOutput output, ServiceIdentifier serviceIdentifier) throws IOException {
        output.writeUTF(serviceIdentifier.toString());
    }

    private void write(ObjectOutput output, ContextIdentifier contextIdentifier) throws IOException {
        output.writeUTF(contextIdentifier.toString());
    }

    private void write(ObjectOutput output, RequestIdentifier requestIdentifier) throws IOException {
        output.writeUTF(requestIdentifier.toString());
    }

    private void write(ObjectOutput output, StreamIdentifier streamIdentifier) throws IOException {
        output.writeUTF(streamIdentifier.toString());
    }

    private final class ProtocolHandlerImpl implements ProtocolHandler {

        public void sendServiceActivate(final ServiceIdentifier remoteServiceIdentifier) throws IOException {
            outgoingQueue.add(new OutputAction() {
                public void run(ByteMessageOutput target) throws IOException {
                    final ObjectMessageOutput msgOutput = protocolContext.getMessageOutput(target);
                    write(msgOutput, MsgType.SERVICE_ACTIVATE);
                    write(msgOutput, remoteServiceIdentifier);
                    msgOutput.commit();
                }
            });
        }

        public void sendReply(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier, final Object reply) throws IOException {
            // we have to buffer because reply might be mutable!
            final BufferedByteMessageOutput output = new BufferedByteMessageOutput(256);
            final ObjectMessageOutput msgOutput = protocolContext.getMessageOutput(output);
            write(msgOutput, MsgType.REPLY);
            write(msgOutput, remoteContextIdentifier);
            write(msgOutput, requestIdentifier);
            msgOutput.writeObject(reply);
            msgOutput.commit();
        }

        public void sendException(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) throws IOException {
            // we have to buffer because exception might contain mutable elements
            final BufferedByteMessageOutput output = new BufferedByteMessageOutput(256);
            final ObjectMessageOutput msgOutput = protocolContext.getMessageOutput(output);
            write(msgOutput, MsgType.EXCEPTION);
            write(msgOutput, remoteContextIdentifier);
            write(msgOutput, requestIdentifier);
            msgOutput.writeObject(exception);
            msgOutput.commit();
        }

        public void sendCancelAcknowledge(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier) throws IOException {
            outgoingQueue.add(new OutputAction() {
                public void run(ByteMessageOutput target) throws IOException {
                    final ObjectMessageOutput msgOutput = protocolContext.getMessageOutput(target);
                    write(msgOutput, MsgType.CANCEL_ACK);
                    write(msgOutput, remoteContextIdentifier);
                    write(msgOutput, requestIdentifier);
                    msgOutput.commit();
                }
            });
        }

        public void sendServiceClosing(final ServiceIdentifier remoteServiceIdentifier) throws IOException {
            outgoingQueue.add(new OutputAction() {
                public void run(ByteMessageOutput target) throws IOException {
                    final ObjectMessageOutput msgOutput = protocolContext.getMessageOutput(target);
                    write(msgOutput, MsgType.SERVICE_TERMINATE);
                    write(msgOutput, remoteServiceIdentifier);
                    msgOutput.commit();
                }
            });
        }

        public void sendContextClosing(final ContextIdentifier remoteContextIdentifier, final boolean done) throws IOException {
        }

        public ContextIdentifier getLocalRootContextIdentifier() {
            return null;
        }

        public ContextIdentifier getRemoteRootContextIdentifier() {
            return null;
        }

        public ContextIdentifier openContext(final ServiceIdentifier serviceIdentifier) throws IOException {
            final ContextIdentifier contextIdentifier = null;
            outgoingQueue.add(new OutputAction() {
                public void run(ByteMessageOutput target) throws IOException {
                    final ObjectMessageOutput msgOutput = protocolContext.getMessageOutput(target);
                    write(msgOutput, MsgType.CONTEXT_OPENED);
                    write(msgOutput, serviceIdentifier);
                    write(msgOutput, contextIdentifier);
                    msgOutput.commit();
                }
            });
            return contextIdentifier;
        }

        public void sendContextClose(final ContextIdentifier contextIdentifier, final boolean immediate, final boolean cancel, final boolean interrupt) throws IOException {
            outgoingQueue.add(new OutputAction() {
                public void run(ByteMessageOutput target) throws IOException {
                    final ObjectMessageOutput msgOutput = protocolContext.getMessageOutput(target);
                    write(msgOutput, MsgType.CLOSE_CONTEXT);
                    write(msgOutput, contextIdentifier);
                    msgOutput.commit();
                }
            });
        }

        public RequestIdentifier openRequest(ContextIdentifier contextIdentifier) throws IOException {
            return null;
        }

        public ServiceIdentifier openService() throws IOException {
            return null;
        }

        public void sendServiceClose(final ServiceIdentifier serviceIdentifier) throws IOException {
            outgoingQueue.add(new OutputAction() {
                public void run(ByteMessageOutput target) throws IOException {
                    final ObjectMessageOutput msgOutput = protocolContext.getMessageOutput(target);
                    write(msgOutput, MsgType.CLOSE_SERVICE);
                    write(msgOutput, serviceIdentifier);
                    msgOutput.commit();
                }
            });
        }

        public void sendRequest(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final Object request, final Executor streamExecutor) throws IOException {
            // we have to buffer because request might be mutable!
            final BufferedByteMessageOutput output = new BufferedByteMessageOutput(256);
            final ObjectMessageOutput msgOutput = protocolContext.getMessageOutput(output, streamExecutor);
            write(msgOutput, MsgType.REQUEST);
            write(msgOutput, contextIdentifier);
            write(msgOutput, requestIdentifier);
            msgOutput.writeObject(request);
            msgOutput.commit();
        }

        public void sendCancelRequest(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final boolean mayInterrupt) throws IOException {
            outgoingQueue.add(new OutputAction() {
                public void run(ByteMessageOutput target) throws IOException {
                    final ObjectMessageOutput msgOutput = protocolContext.getMessageOutput(target);
                    write(msgOutput, MsgType.CANCEL_REQUEST);
                    write(msgOutput, contextIdentifier);
                    write(msgOutput, requestIdentifier);
                    msgOutput.writeBoolean(mayInterrupt);
                    msgOutput.commit();
                }
            });
        }

        public ContextIdentifier openContext() throws IOException {
            return null;
        }

        public StreamIdentifier openStream() throws IOException {
            return null;
        }

        public void closeStream(final StreamIdentifier streamIdentifier) throws IOException {
            outgoingQueue.add(new OutputAction() {
                public void run(ByteMessageOutput target) throws IOException {
                    final ObjectMessageOutput msgOutput = protocolContext.getMessageOutput(target);
                    write(msgOutput, MsgType.CLOSE_STREAM);
                    write(msgOutput, streamIdentifier);
                    msgOutput.commit();
                }
            });
        }

        public StreamIdentifier readStreamIdentifier(ObjectInput input) throws IOException {
            return null;
        }

        public void writeStreamIdentifier(ObjectOutput output, StreamIdentifier identifier) throws IOException {
            write(output, identifier);
        }

        public ObjectMessageOutput sendStreamData(StreamIdentifier streamIdentifier, Executor streamExecutor) throws IOException {
            return protocolContext.getMessageOutput(new BufferedByteMessageOutput(256), streamExecutor);
        }

        public void closeSession() throws IOException {
        }

        public String getRemoteEndpointName() {
            return null;
        }
    }

    public class BufferedByteMessageOutput implements ByteMessageOutput, OutputAction {
        private final int bufsize;
        private final List<byte[]> bufferList = new ArrayList<byte[]>();
        private int sizeOfLast;

        public BufferedByteMessageOutput(final int bufsize) {
            this.bufsize = bufsize;
        }

        public void write(int b) throws IOException {
            final byte[] last = bufferList.get(bufferList.size());
            if (sizeOfLast == last.length) {
                final byte[] bytes = new byte[bufsize];
                bufferList.add(bytes);
                bytes[0] = (byte) b;
                sizeOfLast = 1;
            } else {
                last[sizeOfLast++] = (byte) b;
            }
        }

        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        public void write(byte[] b, int offs, int len) throws IOException {
            byte[] bytes = bufferList.get(bufferList.size());
            while (len > 0) {
                final int copySize = bytes.length - sizeOfLast;
                if (len <= copySize) {
                    System.arraycopy(b, offs, bytes, sizeOfLast, len);
                    sizeOfLast += len;
                    return;
                } else {
                    System.arraycopy(b, offs, bytes, sizeOfLast, copySize);
                    bytes = new byte[bufsize];
                    bufferList.add(bytes);
                    sizeOfLast = 0;
                    len -= copySize;
                    offs += copySize;
                }
            }
        }

        public void commit() throws IOException {
            outgoingQueue.add(this);
        }

        public int getBytesWritten() throws IOException {
            Iterator<byte[]> it = bufferList.iterator();
            if (! it.hasNext()) {
                return 0;
            }
            int t = 0;
            for (;;) {
                byte[] b = it.next();
                if (it.hasNext()) {
                    t += b.length;
                } else {
                    return t + sizeOfLast;
                }
            }
        }

        public void close() throws IOException {
            bufferList.clear();
        }

        public void flush() throws IOException {
        }

        public void run(ByteMessageOutput output) throws IOException {
            final Iterator<byte[]> iterator = bufferList.iterator();
            if (! iterator.hasNext()) {
                return;
            }
            for (;;) {
                byte[] bytes = iterator.next();
                if (iterator.hasNext()) {
                    output.write(bytes);
                } else {
                    output.write(bytes, 0, sizeOfLast);
                    return;
                }
            }
        }
    }

    private void addSessionHeader(final OutgoingHttpMessage msg) {
        msg.addHeader(Http.HEADER_SESSION_ID, sessionId);
    }

    private interface OutputAction {
        void run(ByteMessageOutput target) throws IOException;
    }

    private final class OutgoingActionHttpMessage extends AbstractOutgoingHttpMessage {
        private final List<OutputAction> actions;
        private final long sequenceValue;

        public OutgoingActionHttpMessage(final List<OutputAction> actions) {
            this.actions = actions;
            sequenceValue = outputSequence.getAndIncrement();
            addSessionHeader(this);
            addHeader(Http.HEADER_SEQ, Long.toString(sequenceValue, 16));
        }

        public void writeMessageData(ByteMessageOutput byteOutput) throws IOException {
            final ObjectMessageOutput msgOut = protocolContext.getMessageOutput(byteOutput);
            msgOut.writeInt(PROTOCOL_VERSION);
            write(msgOut, MsgType.DATA_START);
            msgOut.writeLong(sequenceValue);
            msgOut.commit();
            for (OutputAction action : actions) {
                action.run(byteOutput);
            }
            write(msgOut, MsgType.DATA_END);
        }
    }

    // DO NOT re-order
    private enum MsgType {
        DATA_START,
        DATA_END,

        SERVICE_ACTIVATE,
        REPLY,
        EXCEPTION,
        CANCEL_ACK,
        SERVICE_TERMINATE,
        CONTEXT_OPENED,
        CLOSE_CONTEXT,
        SERVICE_REQUEST, CLOSE_SERVICE, REQUEST, CANCEL_REQUEST, CLOSE_STREAM,
    }
}
