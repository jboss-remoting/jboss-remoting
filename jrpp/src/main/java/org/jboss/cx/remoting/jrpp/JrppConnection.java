package org.jboss.cx.remoting.jrpp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.apache.mina.common.AttributeKey;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoBuffer;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.IoSessionInitializer;
import org.apache.mina.filter.sasl.CallbackHandlerFactory;
import org.apache.mina.filter.sasl.SaslClientFactory;
import org.apache.mina.filter.sasl.SaslClientFilter;
import org.apache.mina.filter.sasl.SaslMessageSender;
import org.apache.mina.filter.sasl.SaslServerFactory;
import org.apache.mina.filter.sasl.SaslServerFilter;
import org.apache.mina.handler.multiton.SingleSessionIoHandler;
import org.jboss.cx.remoting.BasicMessage;
import org.jboss.cx.remoting.Header;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.core.util.AtomicStateMachine;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.core.util.Logger;
import org.jboss.cx.remoting.jrpp.id.IdentifierManager;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppRequestIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppServiceIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppStreamIdentifier;
import org.jboss.cx.remoting.jrpp.mina.StreamMarker;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.serial.io.JBossObjectInputStream;
import org.jboss.serial.io.JBossObjectOutputStream;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

/**
 *
 */
public final class JrppConnection {
    /**
     * The protocol version used by this version of Remoting.  Value is transmitted as an unsigned short.
     */
    private static final int PROTOCOL_VERSION = 0x0000;

    private static final AttributeKey JRPP_CONNECTION = new AttributeKey(JrppConnection.class, "jrppConnection");

    private static final String SASL_CLIENT_FILTER_NAME = "SASL client filter";
    private static final String SASL_SERVER_FILTER_NAME = "SASL server filter";

    private IoSession ioSession;
    private final ProtocolHandler protocolHandler;
    private final ProtocolContext protocolContext;
    private final SingleSessionIoHandler ioHandler;
    private final IdentifierManager identifierManager;

    private IOException failureReason;

    /**
     * The negotiated protocol version.  Value is set to {@code min(PROTOCOL_VERSION, remote PROTOCOL_VERSION)}.
     */
    @SuppressWarnings ({"UnusedDeclaration"})
    private int protocolVersion;

    public static SingleSessionIoHandler getHandler(final IoSession session) {
        final JrppConnection connection = (JrppConnection) session.getAttribute(JRPP_CONNECTION);
        return connection.getIoHandler();
    }

    private enum State {
        /** Client side, waiting to receive protocol version info */
        AWAITING_SERVER_VERSION,
        /** Server side, waiting to receive protocol version info */
        AWAITING_CLIENT_VERSION,
        /** Client side, auth phase */
        AWAITING_SERVER_CHALLENGE,
        /** Server side, auth phase */
        AWAITING_CLIENT_RESPONSE,
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
     * @param connector
     * @param remoteAddress
     * @param protocolContext
     * @param clientCallbackHandler
     */
    public JrppConnection(final IoConnector connector, final SocketAddress remoteAddress, final ProtocolContext protocolContext, final CallbackHandler clientCallbackHandler) {
        ioHandler = new IoHandlerImpl();
        final ConnectFuture future = connector.connect(remoteAddress, new IoSessionInitializer<ConnectFuture>() {
            public void initializeSession(final IoSession session, final ConnectFuture future) {
                session.setAttribute(JRPP_CONNECTION, JrppConnection.this);
                JrppConnection.this.ioSession = session;
            }
        });
        // make sure it's initialized for *this* thread as well
        ioSession = future.awaitUninterruptibly().getSession();
        this.protocolContext = protocolContext;

        protocolHandler = new RemotingProtocolHandler();
        identifierManager = new IdentifierManager();
        currentState = AtomicStateMachine.start(State.AWAITING_SERVER_VERSION);
        ioSession.getFilterChain().addLast(SASL_CLIENT_FILTER_NAME, new SaslClientFilter(new SaslClientFactory(){
            public SaslClient createSaslClient(IoSession ioSession, CallbackHandler callbackHandler) throws SaslException {
                return Sasl.createSaslClient(new String[] { "SRP" }, "authz", "JRPP", "server name", Collections.<String,Object>emptyMap(), callbackHandler);
            }
        }, new SaslMessageSender() {
            public void sendSaslMessage(IoSession ioSession, byte[] rawMsgData) throws IOException {
                final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(rawMsgData.length + 30, false);
                write(objectOutputStream, MessageType.SASL_RESPONSE);
                objectOutputStream.write(rawMsgData);
                objectOutputStream.send(ioSession);
            }
        }, new CallbackHandlerFactory() {
            public CallbackHandler getCallbackHandler() {
                return clientCallbackHandler;
            }
        }));
    }

    /**
     * Server side.
     *
     * @param ioSession
     * @param serverContext
     * @throws java.io.IOException
     */
    public JrppConnection(final IoSession ioSession, final ProtocolServerContext serverContext, final CallbackHandler serverCallbackHandler) throws IOException {
        ioSession.setAttribute(JRPP_CONNECTION, this);
        this.ioSession = ioSession;

        protocolHandler = new RemotingProtocolHandler();
        ioHandler = new IoHandlerImpl();

        protocolContext = serverContext.establishSession(protocolHandler);
        identifierManager = new IdentifierManager();
        currentState = AtomicStateMachine.start(State.AWAITING_CLIENT_VERSION);
        ioSession.getFilterChain().addLast(SASL_SERVER_FILTER_NAME, new SaslServerFilter(new SaslServerFactory(){
            public SaslServer createSaslServer(IoSession ioSession, CallbackHandler callbackHandler) throws SaslException {
                return Sasl.createSaslServer("SRP", "JRPP", "server name", Collections.<String,Object>emptyMap(), callbackHandler);
            }
        }, new SaslMessageSender(){
            public void sendSaslMessage(IoSession ioSession, byte[] rawMsgData) throws IOException {
                final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(rawMsgData.length + 30, false);
                write(objectOutputStream, MessageType.SASL_CHALLENGE);
                objectOutputStream.write(rawMsgData);
                objectOutputStream.send(ioSession);
            }
        }, new CallbackHandlerFactory(){
            public CallbackHandler getCallbackHandler() {
                return serverCallbackHandler;
            }
        }));
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

    private void write(ObjectOutputStream objectOutputStream, MessageType messageType) throws IOException {
        objectOutputStream.writeByte(messageType.ordinal());
    }

    private void write(ObjectOutputStream objectOutputStream, ServiceIdentifier serviceIdentifier) throws IOException {
        objectOutputStream.writeShort(((JrppServiceIdentifier)serviceIdentifier).getId());
    }

    private void write(ObjectOutputStream objectOutputStream, ContextIdentifier contextIdentifier) throws IOException {
        objectOutputStream.writeShort(((JrppContextIdentifier)contextIdentifier).getId());
    }

    private void write(ObjectOutputStream objectOutputStream, StreamIdentifier streamIdentifier) throws IOException {
        objectOutputStream.writeShort(((JrppStreamIdentifier)streamIdentifier).getId());
    }

    private void write(ObjectOutputStream objectOutputStream, RequestIdentifier requestIdentifier) throws IOException {
        objectOutputStream.writeShort(((JrppRequestIdentifier)requestIdentifier).getId());
    }

    private void write(ObjectOutputStream objectOutputStream, BasicMessage<?> message) throws IOException {
        objectOutputStream.writeObject(message.getBody());
        final Collection<Header> headers = message.getHeaders();
        objectOutputStream.writeInt(headers.size());
        for (Header header : headers) {
            objectOutputStream.writeUTF(header.getName());
            objectOutputStream.writeUTF(header.getValue());
        }
    }

    public void sendResponse(byte[] rawMsgData) throws IOException {
        final IoBuffer buffer = IoBuffer.allocate(rawMsgData.length + 100);
        final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(buffer);
        write(objectOutputStream, MessageType.SASL_RESPONSE);
        objectOutputStream.write(rawMsgData);
        objectOutputStream.send(ioSession);
    }

    public void sendChallenge(byte[] rawMsgData) throws IOException {
        final IoBuffer buffer = IoBuffer.allocate(rawMsgData.length + 100);
        final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(buffer);
        write(objectOutputStream, MessageType.SASL_CHALLENGE);
        objectOutputStream.write(rawMsgData);
        objectOutputStream.send(ioSession);
    }

    public boolean waitForUp() throws IOException {
        while (! currentState.in(State.UP, State.CLOSED)) {
            currentState.waitForAny();
        }
        return currentState.in(State.UP);
    }

    private void close() {
        currentState.transition(State.CLOSED);
        ioSession.close().awaitUninterruptibly();
        protocolContext.closeSession();
    }

    private final class JrppObjectInputStream extends JBossObjectInputStream {
        public JrppObjectInputStream(final IoBuffer source, final ClassLoader loader) throws IOException {
            super(source.asInputStream(), loader);
        }

        protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
            return super.resolveProxyClass(interfaces);
        }

        protected Class resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            return super.resolveClass(desc);
        }

        protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
            return super.readClassDescriptor();
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

    private static final class JrppObjectOutputStream extends JBossObjectOutputStream {
        private final IoBuffer buffer;

        public JrppObjectOutputStream(final int initialSize, final boolean autoexpand) throws IOException {
            this(IoBuffer.allocate(initialSize).setAutoExpand(autoexpand).skip(4));
        }

        private JrppObjectOutputStream(final IoBuffer buffer) throws IOException {
            super(buffer.asOutputStream(), true);
            this.buffer = buffer;
            enableReplaceObject(true);
        }

//        private static final StackTraceElement[] emptyStack = new StackTraceElement[0];

        protected Object replaceObject(Object obj) throws IOException {
//            if (obj instanceof StackTraceElement[]) {
//                return emptyStack;
//            }
            // todo do stream checks
            return super.replaceObject(obj);
        }

        protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
            super.writeClassDescriptor(desc);
        }

        public void send(IoSession ioSession) throws IOException {
            close();
            ioSession.write(buffer.flip().skip(4));
        }
    }

    public final class RemotingProtocolHandler implements ProtocolHandler {

        public ContextIdentifier openContext(ServiceIdentifier serviceIdentifier) throws IOException {
            final ContextIdentifier contextIdentifier = new JrppContextIdentifier(identifierManager.getIdentifier());
            final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(60, false);
            write(objectOutputStream, MessageType.OPEN_CONTEXT);
            write(objectOutputStream, serviceIdentifier);
            write(objectOutputStream, contextIdentifier);
            objectOutputStream.send(ioSession);
            return contextIdentifier;
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
            final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(60, false);
            write(objectOutputStream, MessageType.CLOSE_SERVICE);
            write(objectOutputStream, serviceIdentifier);
            objectOutputStream.send(ioSession);
        }

        public void closeContext(ContextIdentifier contextIdentifier) throws IOException {
            if (! currentState.in(State.UP)) {
                throw new IllegalStateException("JrppConnection is not in the UP state!");
            }
            final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(60, false);
            write(objectOutputStream, MessageType.CLOSE_CONTEXT);
            write(objectOutputStream, contextIdentifier);
            objectOutputStream.send(ioSession);
        }

        public void closeStream(StreamIdentifier streamIdentifier) throws IOException {
            if (! currentState.in(State.UP)) {
                throw new IllegalStateException("JrppConnection is not in the UP state!");
            }
            if (true /* todo if close not already sent */) {
                // todo mark as sent or remove from table
                final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(60, false);
                write(objectOutputStream, MessageType.CLOSE_STREAM);
                write(objectOutputStream, streamIdentifier);
                objectOutputStream.send(ioSession);
            }
        }

        public void sendServiceRequest(ServiceIdentifier serviceIdentifier, ServiceLocator<?, ?> locator) throws IOException {
            if (! currentState.in(State.UP)) {
                throw new IllegalStateException("JrppConnection is not in the UP state!");
            }
            final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(500, true);
            write(objectOutputStream, MessageType.SERVICE_REQUEST);
            write(objectOutputStream, serviceIdentifier);
            objectOutputStream.writeObject(locator.getRequestType());
            objectOutputStream.writeObject(locator.getReplyType());
            objectOutputStream.writeUTF(locator.getServiceType());
            objectOutputStream.writeUTF(locator.getServiceGroupName());
            final Set<String> interceptors = locator.getAvailableInterceptors();
            final int cnt = interceptors.size();
            objectOutputStream.writeInt(cnt);
            for (String name : interceptors) {
                objectOutputStream.writeUTF(name);
            }
            objectOutputStream.send(ioSession);
        }

        public void sendServiceActivate(ServiceIdentifier serviceIdentifier) throws IOException {
            final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(60, false);
            write(objectOutputStream, MessageType.SERVICE_ACTIVATE);
            write(objectOutputStream, serviceIdentifier);
            objectOutputStream.send(ioSession);
        }

        public void sendReply(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply) throws IOException {
            final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(500, true);
            write(objectOutputStream, MessageType.REPLY);
            write(objectOutputStream, remoteContextIdentifier);
            write(objectOutputStream, requestIdentifier);
            write(objectOutputStream, reply);
            objectOutputStream.send(ioSession);
        }

        public void sendException(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) throws IOException {
            final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(500, true);
            write(objectOutputStream, MessageType.EXCEPTION);
            write(objectOutputStream, remoteContextIdentifier);
            write(objectOutputStream, requestIdentifier);
            objectOutputStream.writeObject(exception);
            objectOutputStream.send(ioSession);
        }

        public void sendRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Request<?> request) throws IOException {
            final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(60, false);
            write(objectOutputStream, MessageType.REQUEST);
            write(objectOutputStream, contextIdentifier);
            write(objectOutputStream, requestIdentifier);
            write(objectOutputStream, request);
            objectOutputStream.send(ioSession);
        }

        public void sendCancelAcknowledge(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier) throws IOException {
            final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(500, true);
            write(objectOutputStream, MessageType.CANCEL_ACK);
            write(objectOutputStream, remoteContextIdentifier);
            write(objectOutputStream, requestIdentifier);
            objectOutputStream.send(ioSession);
        }

        public void sendServiceTerminate(ServiceIdentifier remoteServiceIdentifier) throws IOException {
            final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(60, false);
            write(objectOutputStream, MessageType.SERVICE_TERMINATE);
            write(objectOutputStream, remoteServiceIdentifier);
            objectOutputStream.send(ioSession);
        }

        public void sendCancelRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) throws IOException {
            final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(60, false);
            write(objectOutputStream, MessageType.CANCEL_REQ);
            write(objectOutputStream, contextIdentifier);
            write(objectOutputStream, requestIdentifier);
            objectOutputStream.writeBoolean(mayInterrupt);
            objectOutputStream.send(ioSession);
        }

        public void sendStreamData(StreamIdentifier streamIdentifier, Object data) throws IOException {
            final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(500, true);
            write(objectOutputStream, MessageType.STREAM_DATA);
            write(objectOutputStream, streamIdentifier);
            objectOutputStream.writeObject(data);
            objectOutputStream.send(ioSession);
        }
    }

    private final class IoHandlerImpl implements SingleSessionIoHandler {
        public void sessionCreated() {
        }

        public void sessionOpened() throws IOException {
            // send version info
            final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(60, false);
            write(objectOutputStream, MessageType.VERSION);
            objectOutputStream.writeShort(PROTOCOL_VERSION);
            objectOutputStream.send(ioSession);
        }

        public void sessionClosed() {
            close();
        }

        public void sessionIdle(IdleStatus idleStatus) {
        }

        public void exceptionCaught(Throwable throwable) {
            log.error(throwable, "Exception from JRPP connection handler");
            close();
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
            final boolean trace = log.isTrace();
            IoBuffer buf = (IoBuffer) message;
            final JrppObjectInputStream ois = new JrppObjectInputStream(buf, null /* todo */);
            final MessageType type = MessageType.values()[ois.readByte() & 0xff];
            if (trace) {
                log.trace("Received message of type " + type + " in state " + currentState.getState());
            }
            OUT: switch (currentState.getState()) {
                case AWAITING_CLIENT_VERSION: {
                    switch (type) {
                        case VERSION: {
                            protocolVersion = Math.min(ois.readShort() & 0xffff, PROTOCOL_VERSION);
                            if (trace) {
                                log.trace("Server negotiated protocol version " + protocolVersion);
                            }
                            SaslServerFilter saslServerFilter = getSaslServerFilter();
                            if (saslServerFilter.sendInitialChallenge(ioSession)) {
                                // complete (that was quick!)
                                final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(60, false);
                                write(objectOutputStream, MessageType.AUTH_SUCCESS);
                                objectOutputStream.send(ioSession);
                                currentState.requireTransition(State.AWAITING_CLIENT_VERSION, State.UP);
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
                            if (trace) {
                                log.trace("Client negotiated protocol version " + protocolVersion);
                            }
                            currentState.requireTransition(State.AWAITING_SERVER_VERSION, State.AWAITING_SERVER_CHALLENGE);
                            return;
                        }
                        default: break OUT;
                    }
                }
                case AWAITING_CLIENT_RESPONSE: {
                    switch (type) {
                        case SASL_RESPONSE: {
                            if (trace) {
                                log.trace("Recevied SASL response from client");
                            }
                            byte[] bytes = new byte[buf.remaining()];
                            ois.readFully(bytes);
                            SaslServerFilter saslServerFilter = getSaslServerFilter();
                            try {
                                if (saslServerFilter.handleSaslResponse(ioSession, bytes)) {
                                    final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(60, false);
                                    write(objectOutputStream, MessageType.AUTH_SUCCESS);
                                    objectOutputStream.send(ioSession);
                                    saslServerFilter.startEncryption(ioSession);
                                    currentState.requireTransition(State.AWAITING_CLIENT_RESPONSE, State.UP);
                                }
                            } catch (SaslException ex) {
                                final JrppObjectOutputStream objectOutputStream = new JrppObjectOutputStream(60, false);
                                write(objectOutputStream, MessageType.AUTH_FAILED);
                                objectOutputStream.send(ioSession);
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
                            SaslClientFilter saslClientFilter = getSaslClientFilter();
                            saslClientFilter.handleSaslChallenge(ioSession, bytes);
                            return;
                        }
                        case AUTH_SUCCESS: {
                            SaslClientFilter saslClientFilter = getSaslClientFilter();
                            saslClientFilter.startEncryption(ioSession);
                            currentState.requireTransition(State.AWAITING_SERVER_CHALLENGE, State.UP);
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
                case UP: {
                    switch (type) {
                        case OPEN_CONTEXT: {
                            final ServiceIdentifier serviceIdentifier = readSvcId(ois);
                            final ContextIdentifier contextIdentifier = readCtxtId(ois);
                            protocolContext.receiveOpenedContext(serviceIdentifier, contextIdentifier);
                            return;
                        }
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

        private SaslClientFilter getSaslClientFilter() {
            return (SaslClientFilter) ioSession.getFilterChain().get(SASL_CLIENT_FILTER_NAME);
        }

        private SaslServerFilter getSaslServerFilter() {
            return (SaslServerFilter) ioSession.getFilterChain().get(SASL_SERVER_FILTER_NAME);
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
        OPEN_CONTEXT,
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
