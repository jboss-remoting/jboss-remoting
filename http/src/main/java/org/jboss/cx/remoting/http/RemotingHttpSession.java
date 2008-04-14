package org.jboss.cx.remoting.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.http.spi.AbstractOutgoingHttpMessage;
import org.jboss.cx.remoting.http.spi.IncomingHttpMessage;
import org.jboss.cx.remoting.http.spi.RemotingHttpSessionContext;
import org.jboss.cx.remoting.spi.ByteMessageOutput;
import org.jboss.cx.remoting.spi.ObjectMessageOutput;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import static org.jboss.cx.remoting.util.AtomicStateMachine.start;
import org.jboss.cx.remoting.util.CollectionUtil;

/**
 *
 */
public final class RemotingHttpSession {
//    private final RemotingHttpSessionContext context = new SessionContext();
    private ProtocolContext protocolContext;
    private ProtocolHandler protocolHandler = new ProtocolHandlerImpl();
    private final BlockingQueue<IncomingHttpMessage> incomingQueue = CollectionUtil.synchronizedQueue(new LinkedList<IncomingHttpMessage>());
    private final BlockingQueue<OutputAction> outgoingQueue = CollectionUtil.synchronizedQueue(new LinkedList<OutputAction>());

    private String sessionId;

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public void intialize(final HttpProtocolSupport httpProtocolSupport, final String sessionId, final ProtocolContext protocolContext) {
        
    }

    private enum State implements org.jboss.cx.remoting.util.State<State> {
        INITIAL,
        UP,
        DOWN,
        ;

        public boolean isReachable(final State dest) {
            return compareTo(dest) < 0;
        }
    }

    private final AtomicStateMachine<State> state = start(State.INITIAL);

    private static final int PROTOCOL_VERSION = 0;

    public RemotingHttpSession() {
    }

    public String getSessionId() {
        return sessionId;
    }

    public RemotingHttpSessionContext getContext() {
//        return context;
        return null;
    }

    public ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

//    private final class SessionContext implements RemotingHttpSessionContext {
//        private final Set<ReadyNotifier> readyNotifiers = CollectionUtil.synchronizedSet(new HashSet<ReadyNotifier>());
//
//        public void queueMessage(IncomingHttpMessage message) {
//            incomingQueue.add(message);
//            synchronized(readyNotifiers) {
//                for (ReadyNotifier notifier : readyNotifiers) {
//                    notifier.notifyReady(this);
//                }
//            }
//        }
//
//        public void addReadyNotifier(ReadyNotifier notifier) {
//            readyNotifiers.add(notifier);
//        }
//
//        public OutgoingHttpMessage getNextMessageImmediate() {
//            final List<OutputAction> actions = CollectionUtil.arrayList();
//            outgoingQueue.drainTo(actions);
//            if (actions.isEmpty()) {
//                return null;
//            }
//            return new OutgoingActionHttpMessage(actions);
//        }
//
//        public OutgoingHttpMessage getNextMessage(long timeoutMillis) throws InterruptedException {
//            synchronized(outgoingQueue) {
//                final OutputAction first = outgoingQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
//                if (first != null) {
//                    final List<OutputAction> actions = CollectionUtil.arrayList();
//                    actions.add(first);
//                    outgoingQueue.drainTo(actions);
//                    return new OutgoingActionHttpMessage(actions);
//                } else {
//                    return null;
//                }
//            }
//        }
//    }

    private final class ProtocolHandlerImpl implements ProtocolHandler {

        public void sendReply(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier, final Object reply) throws IOException {
        }

        public void sendException(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) throws IOException {
        }

        public void sendCancelAcknowledge(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier) throws IOException {
        }

        public void sendServiceClosing(final ServiceIdentifier remoteServiceIdentifier) throws IOException {
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
            return null;
        }

        public void sendContextClose(final ContextIdentifier contextIdentifier, final boolean immediate, final boolean cancel, final boolean interrupt) throws IOException {
        }

        public RequestIdentifier openRequest(final ContextIdentifier contextIdentifier) throws IOException {
            return null;
        }

        public void sendServiceClose(final ServiceIdentifier serviceIdentifier) throws IOException {
        }

        public void sendRequest(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final Object request, final Executor streamExecutor) throws IOException {
        }

        public void sendCancelRequest(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final boolean mayInterrupt) throws IOException {
        }

        public ContextIdentifier openContext() throws IOException {
            return null;
        }

        public ServiceIdentifier openService() throws IOException {
            return null;
        }

        public StreamIdentifier openStream() throws IOException {
            return null;
        }

        public void closeStream(final StreamIdentifier streamIdentifier) throws IOException {
        }

        public ObjectMessageOutput sendStreamData(final StreamIdentifier streamIdentifier, final long sequence, final Executor streamExecutor) throws IOException {
            return null;
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

    private final class OutgoingActionHttpMessage extends AbstractOutgoingHttpMessage {
        private final List<OutputAction> actions;

        public OutgoingActionHttpMessage(final List<OutputAction> actions) {
            this.actions = actions;
        }

        public void writeMessageData(ByteMessageOutput byteOutput) throws IOException {
            final ObjectMessageOutput msgOut = protocolContext.getMessageOutput(byteOutput);
            msgOut.writeInt(PROTOCOL_VERSION);
            msgOut.commit();
            for (OutputAction action : actions) {
                action.run(byteOutput);
            }
        }
    }
}
