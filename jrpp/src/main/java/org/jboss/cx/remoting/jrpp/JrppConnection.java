package org.jboss.cx.remoting.jrpp;

import org.apache.mina.common.IoSession;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoBuffer;
import org.apache.mina.common.AttributeKey;
import org.apache.mina.handler.multiton.SingleSessionIoHandler;
import org.apache.mina.filter.sasl.SaslServerFilter;
import org.apache.mina.filter.sasl.SaslClientFilter;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.Header;
import org.jboss.cx.remoting.BasicMessage;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.core.util.Logger;
import org.jboss.cx.remoting.core.AtomicStateMachine;
import org.jboss.cx.remoting.jrpp.id.IdentifierManager;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppRequestIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppStreamIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppServiceIdentifier;
import org.jboss.cx.remoting.jrpp.mina.StreamMarker;
import org.jboss.serial.io.JBossObjectInputStream;
import org.jboss.serial.io.JBossObjectOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.ObjectInputStream;
import java.util.Collection;
import java.util.Set;

import javax.security.sasl.SaslException;

/**
 *
 */
public final class JrppConnection {
    /**
     * The protocol version used by this version of Remoting.  Value is transmitted as an unsigned short.
     */
    private static final int PROTOCOL_VERSION = 0x0000;

    private static final AttributeKey JRPP_CONNECTION = new AttributeKey(JrppConnection.class, "jrppConnection");

    private final IoSession ioSession;
    private final ProtocolHandler protocolHandler;
    private final ProtocolContext protocolContext;
    private final SingleSessionIoHandler ioHandler;
    private final IdentifierManager identifierManager;
    private final JrppObjectOutputStream objectOutputStream;

    /**
     * The negotiated protocol version.  Value is set to {@code min(PROTOCOL_VERSION, remote PROTOCOL_VERSION)}.
     */
    @SuppressWarnings ({"UnusedDeclaration"})
    private int protocolVersion;

    private enum State {
        /** Client side, waiting to receive protocol version info */
        AWAITING_SERVER_VERSION,
        /** Server side, waiting to receive protocol version info */
        AWAITING_CLIENT_VERSION,
        /** Client side, phase 1 (server auths client) */
        AWAITING_SERVER_CHALLENGE,
        /** Server side, phase 1 (server auths client) */
        AWAITING_CLIENT_RESPONSE,
        /** Client side, phase 2 (client auths server) */
        AWAITING_SERVER_RESPONSE,
        /** Server side, phase 2 (client auths server) */
        AWAITING_CLIENT_CHALLENGE,
        /** Connection is up */
        UP,
        /** Session is shutting down or closed */
        CLOSED,
    }

    private AtomicStateMachine<State> currentState;

    private static final Logger log = Logger.getLogger(JrppConnection.class);

    /**
     * Client side.
     *
     * @param ioSession
     * @param protocolContext
     * @throws IOException
     */
    public JrppConnection(final IoSession ioSession, final ProtocolContext protocolContext) throws IOException {
        this.ioSession = ioSession;
        this.protocolContext = protocolContext;

        protocolHandler = new RemotingProtocolHandler();
        ioHandler = new IoHandlerImpl();
        identifierManager = new IdentifierManager();
        objectOutputStream = new JrppObjectOutputStream(new BufferedOutputStream(new IoSessionOutputStream()), false);
        currentState = AtomicStateMachine.start(State.AWAITING_SERVER_CHALLENGE);
        ioSession.setAttribute(JRPP_CONNECTION, this);
    }

    /**
     * Server side.
     *
     * @param ioSession
     * @param serverContext
     * @throws IOException
     */
    public JrppConnection(final IoSession ioSession, final ProtocolServerContext serverContext) throws IOException {
        this.ioSession = ioSession;

        protocolHandler = new RemotingProtocolHandler();
        ioHandler = new IoHandlerImpl();

        protocolContext = serverContext.establishSession(protocolHandler);
        identifierManager = new IdentifierManager();
        objectOutputStream = new JrppObjectOutputStream(new IoSessionOutputStream(), false);
        currentState = AtomicStateMachine.start(State.AWAITING_CLIENT_RESPONSE);
        ioSession.setAttribute(JRPP_CONNECTION, this);
    }

    public static JrppConnection getConnection(IoSession ioSession) {
        return (JrppConnection) ioSession.getAttribute(JRPP_CONNECTION);
    }

    public IoSession getIoSession() {
        return ioSession;
    }

    public SingleSessionIoHandler getIoHandler() {
        return ioHandler;
    }

    public ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    public ProtocolContext getProtocolContext() {
        return protocolContext;
    }

    private void write(MessageType messageType) throws IOException {
        objectOutputStream.writeByte(messageType.ordinal());
    }

    private void write(ServiceIdentifier serviceIdentifier) throws IOException {
        objectOutputStream.writeShort(((JrppServiceIdentifier)serviceIdentifier).getId());
    }

    private void write(ContextIdentifier contextIdentifier) throws IOException {
        objectOutputStream.writeShort(((JrppContextIdentifier)contextIdentifier).getId());
    }

    private void write(StreamIdentifier streamIdentifier) throws IOException {
        objectOutputStream.writeShort(((JrppStreamIdentifier)streamIdentifier).getId());
    }

    private void write(RequestIdentifier requestIdentifier) throws IOException {
        objectOutputStream.writeShort(((JrppRequestIdentifier)requestIdentifier).getId());
    }

    private void write(BasicMessage<?> message) throws IOException {
        objectOutputStream.writeObject(message.getBody());
        final Collection<Header> headers = message.getHeaders();
        objectOutputStream.writeInt(headers.size());
        for (Header header : headers) {
            objectOutputStream.writeUTF(header.getName());
            objectOutputStream.writeUTF(header.getValue());
        }
    }

    public void sendResponse(final byte[] rawMsgData) throws IOException {
        write(MessageType.SASL_RESPONSE);
        objectOutputStream.write(rawMsgData);
        objectOutputStream.flush();
    }

    public void sendChallenge(final byte[] rawMsgData) throws IOException {
        write(MessageType.SASL_CHALLENGE);
        objectOutputStream.write(rawMsgData);
        objectOutputStream.flush();
    }

    public boolean waitForUp() {
        while (! currentState.in(State.UP, State.CLOSED)) {
            currentState.waitUninterruptiplyForAny();
        }
        return currentState.in(State.UP);
    }

    private void close() {
        currentState.transition(State.CLOSED);
        ioSession.close();
        protocolContext.closeSession();
    }

    private final class JrppObjectInputStream extends JBossObjectInputStream {
        public JrppObjectInputStream(final InputStream is, final ClassLoader loader) throws IOException {
            super(is, loader);
        }

        protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
            return super.resolveProxyClass(interfaces);
        }

        public Object readObjectOverride() throws IOException, ClassNotFoundException {
            final Object o = super.readObjectOverride();
            if (o instanceof StreamMarker) {
                //uh ,do something
                return null;
            } else {
                return o;
            }
        }
    }

    private final class JrppObjectOutputStream extends JBossObjectOutputStream {
        public JrppObjectOutputStream(final OutputStream output, final boolean checkSerializableClass) throws IOException {
            super(output, checkSerializableClass);
        }

        protected Object replaceObject(Object obj) throws IOException {
            return super.replaceObject(obj);
        }

        public void flush() throws IOException {
            super.flush();
            reset();
        }
    }

    private final class IoSessionOutputStream extends OutputStream {
        private IoBuffer target;

        public IoSessionOutputStream() {
            allocate();
        }

        public void write(int b) throws IOException {
            target.put((byte)b);
        }

        public void write(byte b[], int off, int len) throws IOException {
            target.put(b, off, len);
        }

        public void flush() throws IOException {
            if (target.position() > 0) {
                ioSession.write(target.flip().skip(4));
                allocate();
            }
        }

        private void allocate() {
            target = IoBuffer.allocate(2048);
            target.skip(4);
        }
    }

    public final class RemotingProtocolHandler implements ProtocolHandler {

        public ContextIdentifier openContext(ServiceIdentifier serviceIdentifier) throws IOException {
            return new JrppContextIdentifier(identifierManager.getIdentifier());
        }

        public RequestIdentifier openRequest(ContextIdentifier contextIdentifier) throws IOException {
            return new JrppRequestIdentifier(identifierManager.getIdentifier());
        }

        public StreamIdentifier openStream() throws IOException {
            return new JrppStreamIdentifier(identifierManager.getIdentifier());
        }

        public ServiceIdentifier openService() throws IOException {
            return new JrppServiceIdentifier(identifierManager.getIdentifier());
        }

        public void closeSession() throws IOException {
            if (currentState.transition(State.CLOSED)) {
                // todo - maybe we don't need to wait?
                ioSession.close().awaitUninterruptibly();
            }
        }

        public void closeService(ServiceIdentifier serviceIdentifier) throws IOException {
            if (! currentState.in(State.UP)) {
                throw new IllegalStateException("JrppConnection is not in the UP state!");
            }
            write(MessageType.CLOSE_SERVICE);
            write(serviceIdentifier);
            objectOutputStream.flush();
        }

        public void closeContext(ContextIdentifier contextIdentifier) throws IOException {
            if (! currentState.in(State.UP)) {
                throw new IllegalStateException("JrppConnection is not in the UP state!");
            }
            write(MessageType.CLOSE_CONTEXT);
            write(contextIdentifier);
            objectOutputStream.flush();
        }

        public void closeStream(StreamIdentifier streamIdentifier) throws IOException {
            if (! currentState.in(State.UP)) {
                throw new IllegalStateException("JrppConnection is not in the UP state!");
            }
            if (true /* todo if close not already sent */) {
                // todo mark as sent or remove from table
                write(MessageType.CLOSE_STREAM);
                write(streamIdentifier);
                objectOutputStream.flush();
            }
        }

        public void sendServiceRequest(ServiceIdentifier serviceIdentifier, ServiceLocator<?, ?> locator) throws IOException {
            if (! currentState.in(State.UP)) {
                throw new IllegalStateException("JrppConnection is not in the UP state!");
            }
            write(MessageType.SERVICE_REQUEST);
            write(serviceIdentifier);
            objectOutputStream.writeObject(locator.getRequestType());
            objectOutputStream.writeObject(locator.getReplyType());
            objectOutputStream.writeObject(locator.getEndpointName());
            objectOutputStream.writeObject(locator.getServiceType());
            objectOutputStream.writeObject(locator.getServiceGroupName());
            final Set<String> interceptors = locator.getAvailableInterceptors();
            final int cnt = interceptors.size();
            objectOutputStream.writeInt(cnt);
            for (String name : interceptors) {
                objectOutputStream.writeUTF(name);
            }
            objectOutputStream.flush();
        }

        public void sendServiceActivate(ServiceIdentifier serviceIdentifier) throws IOException {
            write(MessageType.SERVICE_ACTIVATE);
            write(serviceIdentifier);
            objectOutputStream.flush();
        }

        public void sendReply(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply) throws IOException {
            write(MessageType.REPLY);
            write(remoteContextIdentifier);
            write(requestIdentifier);
            write(reply);
            objectOutputStream.flush();
        }

        public void sendException(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) throws IOException {
            write(MessageType.EXCEPTION);
            write(remoteContextIdentifier);
            write(requestIdentifier);
            objectOutputStream.writeObject(exception);
            objectOutputStream.flush();
        }

        public void sendRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Request<?> request) throws IOException {
            write(MessageType.REQUEST);
            write(contextIdentifier);
            write(requestIdentifier);
            write(request);
            objectOutputStream.flush();
        }

        public void sendCancelAcknowledge(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier) throws IOException {
            write(MessageType.CANCEL_ACK);
            write(remoteContextIdentifier);
            write(requestIdentifier);
            objectOutputStream.flush();
        }

        public void sendServiceTerminate(ServiceIdentifier remoteServiceIdentifier) throws IOException {
            write(MessageType.SERVICE_TERMINATE);
            write(remoteServiceIdentifier);
            objectOutputStream.flush();
        }

        public void sendCancelRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) throws IOException {
            write(MessageType.CANCEL_REQ);
            write(contextIdentifier);
            write(requestIdentifier);
            objectOutputStream.writeBoolean(mayInterrupt);
            objectOutputStream.flush();
        }

        public void sendStreamData(StreamIdentifier streamIdentifier, Object data) throws IOException {
            write(MessageType.STREAM_DATA);
            write(streamIdentifier);
            objectOutputStream.writeObject(data);
            objectOutputStream.flush();
        }
    }

    private final class IoHandlerImpl implements SingleSessionIoHandler {
        public void sessionCreated() {
        }

        public void sessionOpened() throws IOException {
            // send version info
            write(MessageType.VERSION);
            objectOutputStream.writeShort(PROTOCOL_VERSION);
            objectOutputStream.flush();
        }

        public void sessionClosed() {
            protocolContext.closeSession();
        }

        public void sessionIdle(IdleStatus idleStatus) {
        }

        public void exceptionCaught(Throwable throwable) {
            // todo log exception
        }

        private void readHeaders(ObjectInputStream ois, BasicMessage<?> msg) throws IOException {
            final int cnt = ois.readInt();
            for (int i = 0; i < cnt; i ++) {
                final String name = ois.readUTF();
                final String value = ois.readUTF();
                msg.addHeader(name, value);
            }
        }

        private ContextIdentifier readCtxtId(ObjectInputStream ois) throws IOException {
            return new JrppContextIdentifier(ois.readShort());
        }

        private ServiceIdentifier readSvcId(ObjectInputStream ois) throws IOException {
            return new JrppServiceIdentifier(ois.readShort());
        }

        private StreamIdentifier readStrId(ObjectInputStream ois) throws IOException {
            return new JrppStreamIdentifier(ois.readShort());
        }

        private RequestIdentifier readReqId(ObjectInputStream ois) throws IOException {
            return new JrppRequestIdentifier(ois.readShort());
        }

        public void messageReceived(Object message) throws Exception {
            IoBuffer buf = (IoBuffer) message;
            final JrppObjectInputStream ois = new JrppObjectInputStream(buf.asInputStream(), null /* todo */);
            final MessageType type = MessageType.values()[ois.readByte() & 0xff];
            OUT: switch (currentState.getState()) {
                case AWAITING_CLIENT_VERSION: {
                    switch (type) {
                        case VERSION: {
                            protocolVersion = Math.min(ois.readShort() & 0xffff, PROTOCOL_VERSION);
                            SaslServerFilter saslServerFilter = null; // todo
                            if (saslServerFilter.sendInitialChallenge(ioSession)) {
                                // complete; now client may optionally auth server
                                currentState.requireTransition(State.AWAITING_CLIENT_VERSION, State.AWAITING_CLIENT_CHALLENGE);
                            } else {
                                currentState.requireTransition(State.AWAITING_CLIENT_VERSION, State.AWAITING_CLIENT_RESPONSE);
                            }
                            return;
                        }
                        default: break OUT;
                    }
                }
                case AWAITING_SERVER_VERSION: {
                    switch (type) {
                        case VERSION: {
                            protocolVersion = Math.min(ois.readShort() & 0xffff, PROTOCOL_VERSION);
                            currentState.requireTransition(State.AWAITING_SERVER_VERSION, State.AWAITING_SERVER_CHALLENGE);
                            return;
                        }
                        default: break OUT;
                    }
                }
                case AWAITING_CLIENT_RESPONSE: {
                    switch (type) {
                        case SASL_RESPONSE: {
                            byte[] bytes = new byte[buf.remaining()];
                            ois.readFully(bytes);
                            SaslServerFilter saslServerFilter = null; // todo
                            try {
                                if (saslServerFilter.handleSaslResponse(ioSession, bytes)) {
                                    write(MessageType.AUTH_SUCCESS);
                                    objectOutputStream.flush();
                                    currentState.requireTransition(State.AWAITING_CLIENT_RESPONSE, State.AWAITING_CLIENT_CHALLENGE);
                                }
                            } catch (SaslException ex) {
                                write(MessageType.AUTH_FAILED);
                                objectOutputStream.flush();
                                close();
                                log.info("Client authentication failed (" + ex.getMessage() + ")");
                            }
                            return;
                        }
                        default: break OUT;
                    }
                }
                case AWAITING_SERVER_CHALLENGE: {
                    switch (type) {
                        case SASL_CHALLENGE: {
                            byte[] bytes = new byte[buf.remaining()];
                            ois.readFully(bytes);
                            SaslClientFilter saslClientFilter = null; // todo
                            saslClientFilter.handleSaslChallenge(ioSession, bytes);
                            return;
                        }
                        case AUTH_SUCCESS: {
                            currentState.requireTransition(State.AWAITING_SERVER_CHALLENGE, State.AWAITING_SERVER_RESPONSE);
                            SaslServerFilter saslServerFilter = null; // todo
                            saslServerFilter.sendInitialChallenge(ioSession);
                            return;
                        }
                        case AUTH_FAILED: {
                            log.info("JRPP client rejected authentication");
                            close();
                            return;
                        }
                        default: break OUT;
                    }
                }
                case AWAITING_CLIENT_CHALLENGE: {
                    switch (type) {
                        case SASL_CHALLENGE: {
                            byte[] bytes = new byte[buf.remaining()];
                            ois.readFully(bytes);
                            SaslClientFilter saslClientFilter = null; // todo
                            saslClientFilter.handleSaslChallenge(ioSession, bytes);
                            return;
                        }
                        case AUTH_SUCCESS: {
                            currentState.requireTransition(State.AWAITING_CLIENT_CHALLENGE, State.UP);
                            SaslServerFilter saslServerFilter = null; // todo
                            saslServerFilter.sendInitialChallenge(ioSession);
                            return;
                        }
                        case AUTH_FAILED: {
                            log.info("JRPP client rejected authentication");
                            close();
                            return;
                        }
                        default: break OUT;
                    }
                }
                case AWAITING_SERVER_RESPONSE: {
                    switch (type) {
                        case SASL_RESPONSE: {
                            byte[] bytes = new byte[buf.remaining()];
                            ois.readFully(bytes);
                            SaslServerFilter saslServerFilter = null; // todo
                            try {
                                if (saslServerFilter.handleSaslResponse(ioSession, bytes)) {
                                    write(MessageType.AUTH_SUCCESS);
                                    objectOutputStream.flush();
                                    currentState.requireTransition(State.AWAITING_CLIENT_RESPONSE, State.UP);
                                }
                            } catch (SaslException ex) {
                                write(MessageType.AUTH_FAILED);
                                objectOutputStream.flush();
                                close();
                                log.info("Server authentication failed (" + ex.getMessage() + ")");
                            }
                            return;
                        }
                        default: break OUT;
                    }
                }
                case UP: {
                    switch (type) {
                        case CANCEL_ACK: {
                            final ContextIdentifier contextIdentifier = readCtxtId(ois);
                            final RequestIdentifier requestIdentifier = readReqId(ois);
                            protocolContext.receiveCancelAcknowledge(contextIdentifier, requestIdentifier);
                            return;
                        }
                        case CANCEL_REQ: {
                            final ContextIdentifier contextIdentifier = readCtxtId(ois);
                            final RequestIdentifier requestIdentifier = readReqId(ois);
                            final boolean mayInterrupt = ois.readBoolean();
                            protocolContext.receiveCancelRequest(contextIdentifier, requestIdentifier, mayInterrupt);
                            return;
                        }
                        case CLOSE_CONTEXT: {
                            final ContextIdentifier contextIdentifier = readCtxtId(ois);
                            protocolContext.closeContext(contextIdentifier);
                            return;
                        }
                        case CLOSE_SERVICE: {
                            final ServiceIdentifier serviceIdentifier = readSvcId(ois);
                            protocolContext.closeService(serviceIdentifier);
                            return;
                        }
                        case CLOSE_STREAM: {
                            final StreamIdentifier streamIdentifier = readStrId(ois);
                            protocolContext.closeStream(streamIdentifier);
                            return;
                        }
                        case EXCEPTION: {
                            final ContextIdentifier contextIdentifier = readCtxtId(ois);
                            final RequestIdentifier requestIdentifier = readReqId(ois);
                            final RemoteExecutionException exception = (RemoteExecutionException) ois.readObject();
                            protocolContext.receiveException(contextIdentifier, requestIdentifier, exception);
                            return;
                        }
                        case REPLY: {
                            final ContextIdentifier contextIdentifier = readCtxtId(ois);
                            final RequestIdentifier requestIdentifier = readReqId(ois);
                            final Reply<?> reply = protocolContext.createReply(ois.readObject());
                            readHeaders(ois, reply);
                            protocolContext.receiveReply(contextIdentifier, requestIdentifier, reply);
                            return;
                        }
                        case REQUEST: {
                            final ContextIdentifier contextIdentifier = readCtxtId(ois);
                            final RequestIdentifier requestIdentifier = readReqId(ois);
                            final Request<?> request = protocolContext.createRequest(ois.readObject());
                            readHeaders(ois, request);
                            protocolContext.receiveRequest(contextIdentifier, requestIdentifier, request);
                            return;
                        }
                        case SERVICE_ACTIVATE: {
                            final ServiceIdentifier serviceIdentifier = readSvcId(ois);
                            protocolContext.receiveServiceActivate(serviceIdentifier);
                            return;
                        }
                        case SERVICE_REQUEST: {
                            final ServiceIdentifier serviceIdentifier = readSvcId(ois);
                            final Class<?> requestType = (Class<?>) ois.readObject();
                            final Class<?> replyType = (Class<?>) ois.readObject();
                            final String endpointName = ois.readUTF();
                            final String serviceType = ois.readUTF();
                            final String serviceGroupName = ois.readUTF();
                            final Set<String> interceptors = CollectionUtil.hashSet();
                            int c = ois.readInt();
                            for (int i = 0; i < c; i ++) {
                                interceptors.add(ois.readUTF());
                            }
                            final ServiceLocator<?, ?> locator = ServiceLocator.DEFAULT
                                    .setRequestType(requestType)
                                    .setReplyType(replyType)
                                    .setEndpointName(endpointName)
                                    .setServiceType(serviceType)
                                    .setServiceGroupName(serviceGroupName)
                                    .setAvailableInterceptors(interceptors);
                            protocolContext.receiveServiceRequest(serviceIdentifier, locator);
                            return;
                        }
                        case SERVICE_TERMINATE: {
                            final ServiceIdentifier serviceIdentifier = readSvcId(ois);
                            protocolContext.receiveServiceTerminate(serviceIdentifier);
                            return;
                        }
                        case STREAM_DATA: {
                            final StreamIdentifier streamIdentifier = readStrId(ois);
                            final Object data = ois.readObject();
                            protocolContext.receiveStreamData(streamIdentifier, data);
                            return;
                        }
                        default: break OUT;
                    }
                }
            }
            throw new IllegalStateException("Got message " + type + " during " + currentState);
        }

        public void messageSent(Object object) {
        }
    }

    /**
     * Keep elements in order.  If an element is to be deleted, replace it with a placeholder.
     */
    private enum MessageType {
        VERSION,
        SASL_CHALLENGE,
        SASL_RESPONSE,
        AUTH_SUCCESS,
        AUTH_FAILED,
        CANCEL_ACK,
        CANCEL_REQ,
        CLOSE_CONTEXT,
        CLOSE_SERVICE,
        CLOSE_STREAM,
        EXCEPTION,
        REPLY,
        REQUEST,
        SERVICE_ACTIVATE,
        SERVICE_REQUEST,
        SERVICE_TERMINATE,
        STREAM_DATA,
    }

}
