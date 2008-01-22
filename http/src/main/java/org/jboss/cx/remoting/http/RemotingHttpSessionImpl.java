package org.jboss.cx.remoting.http;

import org.jboss.cx.remoting.http.spi.RemotingHttpSessionContext;
import org.jboss.cx.remoting.http.spi.IncomingHttpMessage;
import org.jboss.cx.remoting.http.spi.ReadyNotifier;
import org.jboss.cx.remoting.http.spi.OutgoingHttpMessage;
import org.jboss.cx.remoting.http.spi.AbstractOutgoingHttpMessage;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.core.util.MessageOutput;
import org.jboss.cx.remoting.core.util.ByteOutput;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.Request;

import javax.security.auth.callback.CallbackHandler;

import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
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
    private final BlockingQueue<OutgoingHttpMessage> outgoingQueue = CollectionUtil.synchronizedQueue(new LinkedList<OutgoingHttpMessage>());
    private final String sessionId;
    private final CallbackHandler callbackHandler;
    private final AtomicLong msgSequence = new AtomicLong(0L);

    public RemotingHttpSessionImpl(final HttpProtocolSupport protocolSupport, final ProtocolContext protocolContext, final CallbackHandler callbackHandler) {
        this.protocolContext = protocolContext;
        this.callbackHandler = callbackHandler;
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
            return outgoingQueue.poll();
        }

        public OutgoingHttpMessage getNextMessage(long timeoutMillis) throws InterruptedException {
            return outgoingQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        public CallbackHandler getCallbackHandler() {
            return callbackHandler;
        }
    }

    private final class ProtocolHandlerImpl implements ProtocolHandler {

        public void sendServiceActivate(ServiceIdentifier remoteServiceIdentifier) throws IOException {
            OutgoingHttpMessage msg = new AbstractOutgoingHttpMessage(){
                public void writeMessageData(ByteOutput byteOutput) throws IOException {
                }
            };
            addSessionHeader(msg);
            
            outgoingQueue.add(msg);
        }

        public void sendReply(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply) throws IOException {
        }

        public void sendException(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) throws IOException {
        }

        public void sendCancelAcknowledge(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier) throws IOException {
        }

        public void sendServiceTerminate(ServiceIdentifier remoteServiceIdentifier) throws IOException {
        }

        public ContextIdentifier openContext(ServiceIdentifier serviceIdentifier) throws IOException {
            return null;
        }

        public void closeContext(ContextIdentifier contextIdentifier) throws IOException {
        }

        public RequestIdentifier openRequest(ContextIdentifier contextIdentifier) throws IOException {
            return null;
        }

        public ServiceIdentifier openService() throws IOException {
            return null;
        }

        public void sendServiceRequest(ServiceIdentifier serviceIdentifier, ServiceLocator<?, ?> locator) throws IOException {
        }

        public void closeService(ServiceIdentifier serviceIdentifier) throws IOException {
        }

        public void sendRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Request<?> request, Executor streamExecutor) throws IOException {
        }

        public void sendCancelRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) throws IOException {
        }

        public StreamIdentifier openStream() throws IOException {
            return null;
        }

        public void closeStream(StreamIdentifier streamIdentifier) throws IOException {
        }

        public StreamIdentifier readStreamIdentifier(ObjectInput input) throws IOException {
            return null;
        }

        public void writeStreamIdentifier(ObjectOutput output, StreamIdentifier identifier) throws IOException {
        }

        public MessageOutput sendStreamData(StreamIdentifier streamIdentifier, Executor streamExecutor) throws IOException {
            return null;
        }

        public void closeSession() throws IOException {
        }

        public String getRemoteEndpointName() {
            return null;
        }
    }

    private void addSessionHeader(final OutgoingHttpMessage msg) {
        msg.addHeader(HttpProtocolSupport.HEADER_SESSION_ID, sessionId);
    }
}
