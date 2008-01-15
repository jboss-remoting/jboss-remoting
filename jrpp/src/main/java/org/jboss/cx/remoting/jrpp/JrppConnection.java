package org.jboss.cx.remoting.jrpp;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.SocketAddress;
import java.net.URI;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executor;
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
import org.jboss.cx.remoting.jrpp.mina.IoBufferByteInput;
import org.jboss.cx.remoting.jrpp.mina.IoBufferByteOutput;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.MessageInput;
import org.jboss.cx.remoting.spi.protocol.MessageOutput;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;

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

    private String remoteName;

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
     * @param uri
     * @param protocolContext
     * @param clientCallbackHandler
     */
    public JrppConnection(final IoConnector connector, final URI uri, final ProtocolContext protocolContext, final CallbackHandler clientCallbackHandler) {
        // todo - this seems very iffy to me, since we're basically leaking "this" before constructor is done
        this.protocolContext = protocolContext;
        ioHandler = new IoHandlerImpl();
        final ConnectFuture future = connector.connect(new InetSocketAddress(uri.getHost(), uri.getPort()), new IoSessionInitializer<ConnectFuture>() {
            public void initializeSession(final IoSession session, final ConnectFuture future) {
                session.setAttribute(JRPP_CONNECTION, JrppConnection.this);
                JrppConnection.this.ioSession = session;
            }
        });
        // make sure it's initialized for *this* thread as well
        ioSession = future.awaitUninterruptibly().getSession();

        protocolHandler = new RemotingProtocolHandler();
        identifierManager = new IdentifierManager();
        currentState = AtomicStateMachine.start(State.AWAITING_SERVER_VERSION);
        ioSession.getFilterChain().addLast(SASL_CLIENT_FILTER_NAME, new SaslClientFilter(new SaslClientFactory(){
            public SaslClient createSaslClient(IoSession ioSession, CallbackHandler callbackHandler) throws SaslException {
                return Sasl.createSaslClient(new String[] { "SRP" }, protocolContext.getLocalEndpointName(), "JRPP", uri.getHost(), Collections.<String,Object>emptyMap(), callbackHandler);
            }
        }, new SaslMessageSender() {
            public void sendSaslMessage(IoSession ioSession, byte[] rawMsgData) throws IOException {
                final IoBuffer buffer = newBuffer(rawMsgData.length + 30, false);
                final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
                write(output, MessageType.SASL_RESPONSE);
                output.write(rawMsgData);
                output.commit();
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
                return Sasl.createSaslServer("SRP", "JRPP", protocolContext.getLocalEndpointName(), Collections.<String,Object>emptyMap(), callbackHandler);
            }
        }, new SaslMessageSender(){
            public void sendSaslMessage(IoSession ioSession, byte[] rawMsgData) throws IOException {
                final IoBuffer buffer = newBuffer(rawMsgData.length + 30, false);
                final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
                write(output, MessageType.SASL_CHALLENGE);
                output.write(rawMsgData);
                output.commit();
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

    private void write(ObjectOutput output, MessageType messageType) throws IOException {
        output.writeByte(messageType.ordinal());
    }

    private void write(ObjectOutput output, ServiceIdentifier serviceIdentifier) throws IOException {
        output.writeShort(((JrppServiceIdentifier)serviceIdentifier).getId());
    }

    private void write(ObjectOutput output, ContextIdentifier contextIdentifier) throws IOException {
        output.writeShort(((JrppContextIdentifier)contextIdentifier).getId());
    }

    private void write(ObjectOutput output, StreamIdentifier streamIdentifier) throws IOException {
        output.writeShort(((JrppStreamIdentifier)streamIdentifier).getId());
    }

    private void write(ObjectOutput output, RequestIdentifier requestIdentifier) throws IOException {
        output.writeShort(((JrppRequestIdentifier)requestIdentifier).getId());
    }

    private void write(ObjectOutput output, BasicMessage<?> message) throws IOException {
        output.writeObject(message.getBody());
        final Collection<Header> headers = message.getHeaders();
        output.writeInt(headers.size());
        for (Header header : headers) {
            output.writeUTF(header.getName());
            output.writeUTF(header.getValue());
        }
    }

    public void sendResponse(byte[] rawMsgData) throws IOException {
        final IoBuffer buffer = newBuffer(rawMsgData.length + 100, false);
        final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
        write(output, MessageType.SASL_RESPONSE);
        output.write(rawMsgData);
        output.commit();
    }

    public void sendChallenge(byte[] rawMsgData) throws IOException {
        final IoBuffer buffer = newBuffer(rawMsgData.length + 100, false);
        final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
        write(output, MessageType.SASL_CHALLENGE);
        output.write(rawMsgData);
        output.commit();
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

    private static IoBuffer newBuffer(final int initialSize, final boolean autoexpand) {
        return IoBuffer.allocate(initialSize + 4).setAutoExpand(autoexpand).skip(4);
    }

    public final class RemotingProtocolHandler implements ProtocolHandler {

        public ContextIdentifier openContext(ServiceIdentifier serviceIdentifier) throws IOException {
            final ContextIdentifier contextIdentifier = new JrppContextIdentifier(identifierManager.getIdentifier());
            final IoBuffer buffer = newBuffer(60, false);
            final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
            write(output, MessageType.OPEN_CONTEXT);
            write(output, serviceIdentifier);
            write(output, contextIdentifier);
            output.commit();
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

        public String getRemoteEndpointName() {
            return remoteName;
        }

        public void closeService(ServiceIdentifier serviceIdentifier) throws IOException {
            if (! currentState.in(State.UP)) {
                return;
            }
            final IoBuffer buffer = newBuffer(60, false);
            final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
            write(output, MessageType.CLOSE_SERVICE);
            write(output, serviceIdentifier);
            output.commit();
        }

        public void closeContext(ContextIdentifier contextIdentifier) throws IOException {
            if (! currentState.in(State.UP)) {
                return;
            }
            final IoBuffer buffer = newBuffer(60, false);
            final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
            write(output, MessageType.CLOSE_CONTEXT);
            write(output, contextIdentifier);
            output.commit();
        }

        public void closeStream(StreamIdentifier streamIdentifier) throws IOException {
            if (! currentState.in(State.UP)) {
                return;
            }
            if (true /* todo if close not already sent */) {
                // todo mark as sent or remove from table
                final IoBuffer buffer = newBuffer(60, false);
                final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
                write(output, MessageType.CLOSE_STREAM);
                write(output, streamIdentifier);
                output.commit();
            }
        }

        public StreamIdentifier readStreamIdentifier(ObjectInput input) throws IOException {
            return new JrppStreamIdentifier(input);
        }

        public void writeStreamIdentifier(ObjectOutput output, StreamIdentifier identifier) throws IOException {
            write(output, identifier);
        }

        public void sendServiceRequest(ServiceIdentifier serviceIdentifier, ServiceLocator<?, ?> locator) throws IOException {
            if (serviceIdentifier == null) {
                throw new NullPointerException("serviceIdentifier is null");
            }
            if (locator == null) {
                throw new NullPointerException("locator is null");
            }
            if (! currentState.in(State.UP)) {
                throw new IllegalStateException("JrppConnection is not in the UP state!");
            }
            final IoBuffer buffer = newBuffer(500, true);
            final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
            write(output, MessageType.SERVICE_REQUEST);
            write(output, serviceIdentifier);
            output.writeObject(locator.getRequestType());
            output.writeObject(locator.getReplyType());
            output.writeUTF(locator.getServiceType());
            output.writeUTF(locator.getServiceGroupName());
            final Set<String> interceptors = locator.getAvailableInterceptors();
            final int cnt = interceptors.size();
            output.writeInt(cnt);
            for (String name : interceptors) {
                output.writeUTF(name);
            }
            output.commit();
        }

        public void sendServiceActivate(ServiceIdentifier serviceIdentifier) throws IOException {
            if (serviceIdentifier == null) {
                throw new NullPointerException("serviceIdentifier is null");
            }
            final IoBuffer buffer = newBuffer(60, false);
            final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
            write(output, MessageType.SERVICE_ACTIVATE);
            write(output, serviceIdentifier);
            output.commit();
        }

        public void sendReply(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply) throws IOException {
            if (remoteContextIdentifier == null) {
                throw new NullPointerException("remoteContextIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            if (reply == null) {
                throw new NullPointerException("reply is null");
            }
            final IoBuffer buffer = newBuffer(500, true);
            final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
            write(output, MessageType.REPLY);
            write(output, remoteContextIdentifier);
            write(output, requestIdentifier);
            write(output, reply);
            output.commit();
        }

        public void sendException(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) throws IOException {
            if (remoteContextIdentifier == null) {
                throw new NullPointerException("remoteContextIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            if (exception == null) {
                throw new NullPointerException("exception is null");
            }
            final IoBuffer buffer = newBuffer(500, true);
            final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
            write(output, MessageType.EXCEPTION);
            write(output, remoteContextIdentifier);
            write(output, requestIdentifier);
            output.writeObject(exception);
            output.commit();
        }

        public void sendRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Request<?> request, final Executor streamExecutor) throws IOException {
            if (contextIdentifier == null) {
                throw new NullPointerException("contextIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            if (request == null) {
                throw new NullPointerException("request is null");
            }
            final IoBuffer buffer = newBuffer(500, true);
            final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession), streamExecutor);
            write(output, MessageType.REQUEST);
            write(output, contextIdentifier);
            write(output, requestIdentifier);
            write(output, request);
            output.commit();
        }

        public void sendCancelAcknowledge(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier) throws IOException {
            if (remoteContextIdentifier == null) {
                throw new NullPointerException("remoteContextIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final IoBuffer buffer = newBuffer(60, false);
            final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
            write(output, MessageType.CANCEL_ACK);
            write(output, remoteContextIdentifier);
            write(output, requestIdentifier);
            output.commit();
        }

        public void sendServiceTerminate(ServiceIdentifier remoteServiceIdentifier) throws IOException {
            if (remoteServiceIdentifier == null) {
                throw new NullPointerException("remoteServiceIdentifier is null");
            }
            final IoBuffer buffer = newBuffer(60, false);
            final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
            write(output, MessageType.SERVICE_TERMINATE);
            write(output, remoteServiceIdentifier);
            output.commit();
        }

        public void sendCancelRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) throws IOException {
            if (contextIdentifier == null) {
                throw new NullPointerException("contextIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final IoBuffer buffer = newBuffer(60, false);
            final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
            write(output, MessageType.CANCEL_REQ);
            write(output, contextIdentifier);
            write(output, requestIdentifier);
            output.writeBoolean(mayInterrupt);
            output.commit();
        }

        public MessageOutput sendStreamData(StreamIdentifier streamIdentifier, Executor streamExecutor) throws IOException {
            if (streamIdentifier == null) {
                throw new NullPointerException("streamIdentifier is null");
            }
            if (streamExecutor == null) {
                throw new NullPointerException("streamExeceutor is null");
            }
            final IoBuffer buffer = newBuffer(500, true);
            final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession), streamExecutor);
            write(output, MessageType.STREAM_DATA);
            write(output, streamIdentifier);
            return output;
        }
    }

    private final class IoHandlerImpl implements SingleSessionIoHandler {
        public void sessionCreated() {
        }

        public void sessionOpened() throws IOException {
            // send version info
            final IoBuffer buffer = newBuffer(60, false);
            final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
            write(output, MessageType.VERSION);
            output.writeShort(PROTOCOL_VERSION);
            output.writeUTF(protocolContext.getLocalEndpointName());
            output.commit();
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

        private void readHeaders(MessageInput input, BasicMessage<?> msg) throws IOException {
            final int cnt = input.readInt();
            for (int i = 0; i < cnt; i ++) {
                final String name = input.readUTF();
                final String value = input.readUTF();
                msg.addHeader(name, value);
            }
        }

        private ContextIdentifier readCtxtId(MessageInput input) throws IOException {
            return new JrppContextIdentifier(input.readShort());
        }

        private ServiceIdentifier readSvcId(MessageInput input) throws IOException {
            return new JrppServiceIdentifier(input.readShort());
        }

        private StreamIdentifier readStrId(MessageInput input) throws IOException {
            return new JrppStreamIdentifier(input.readShort());
        }

        private RequestIdentifier readReqId(MessageInput input) throws IOException {
            return new JrppRequestIdentifier(input.readShort());
        }

        public void messageReceived(Object message) throws Exception {
            final boolean trace = log.isTrace();
            final MessageInput input = protocolContext.getMessageInput(new IoBufferByteInput((IoBuffer) message));
            final MessageType type = MessageType.values()[input.readByte() & 0xff];
            if (trace) {
                log.trace("Received message of type %s in state %s", type, currentState.getState());
            }
            OUT: switch (currentState.getState()) {
                case AWAITING_CLIENT_VERSION: {
                    switch (type) {
                        case VERSION: {
                            protocolVersion = Math.min(input.readShort() & 0xffff, PROTOCOL_VERSION);
                            if (trace) {
                                log.trace("Server negotiated protocol version " + protocolVersion);
                            }
                            final String name = input.readUTF();
                            remoteName = name.length() > 0 ? name : null;
                            SaslServerFilter saslServerFilter = getSaslServerFilter();
                            if (saslServerFilter.sendInitialChallenge(ioSession)) {
                                // complete (that was quick!)
                                final IoBuffer buffer = newBuffer(60, false);
                                final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
                                write(output, MessageType.AUTH_SUCCESS);
                                output.commit();
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
                            protocolVersion = Math.min(input.readShort() & 0xffff, PROTOCOL_VERSION);
                            if (trace) {
                                log.trace("Client negotiated protocol version " + protocolVersion);
                            }
                            final String name = input.readUTF();
                            remoteName = name.length() > 0 ? name : null;
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
                            byte[] bytes = new byte[input.remaining()];
                            input.readFully(bytes);
                            SaslServerFilter saslServerFilter = getSaslServerFilter();
                            try {
                                if (saslServerFilter.handleSaslResponse(ioSession, bytes)) {
                                    final IoBuffer buffer = newBuffer(60, false);
                                    final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
                                    write(output, MessageType.AUTH_SUCCESS);
                                    output.commit();
                                    saslServerFilter.startEncryption(ioSession);
                                    currentState.requireTransition(State.AWAITING_CLIENT_RESPONSE, State.UP);
                                }
                            } catch (SaslException ex) {
                                final IoBuffer buffer = newBuffer(60, false);
                                final MessageOutput output = protocolContext.getMessageOutput(new IoBufferByteOutput(buffer, ioSession));
                                write(output, MessageType.AUTH_FAILED);
                                output.commit();
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
                            byte[] bytes = new byte[input.remaining()];
                            input.readFully(bytes);
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
                            final ServiceIdentifier serviceIdentifier = readSvcId(input);
                            final ContextIdentifier contextIdentifier = readCtxtId(input);
                            protocolContext.receiveOpenedContext(serviceIdentifier, contextIdentifier);
                            return;
                        }
                        case CANCEL_ACK: {
                            final ContextIdentifier contextIdentifier = readCtxtId(input);
                            final RequestIdentifier requestIdentifier = readReqId(input);
                            protocolContext.receiveCancelAcknowledge(contextIdentifier, requestIdentifier);
                            return;
                        }
                        case CANCEL_REQ: {
                            final ContextIdentifier contextIdentifier = readCtxtId(input);
                            final RequestIdentifier requestIdentifier = readReqId(input);
                            final boolean mayInterrupt = input.readBoolean();
                            protocolContext.receiveCancelRequest(contextIdentifier, requestIdentifier, mayInterrupt);
                            return;
                        }
                        case CLOSE_CONTEXT: {
                            final ContextIdentifier contextIdentifier = readCtxtId(input);
                            protocolContext.closeContext(contextIdentifier);
                            return;
                        }
                        case CLOSE_SERVICE: {
                            final ServiceIdentifier serviceIdentifier = readSvcId(input);
                            protocolContext.closeService(serviceIdentifier);
                            return;
                        }
                        case CLOSE_STREAM: {
                            final StreamIdentifier streamIdentifier = readStrId(input);
                            protocolContext.closeStream(streamIdentifier);
                            return;
                        }
                        case EXCEPTION: {
                            final ContextIdentifier contextIdentifier = readCtxtId(input);
                            final RequestIdentifier requestIdentifier = readReqId(input);
                            final RemoteExecutionException exception = (RemoteExecutionException) input.readObject();
                            protocolContext.receiveException(contextIdentifier, requestIdentifier, exception);
                            return;
                        }
                        case REPLY: {
                            final ContextIdentifier contextIdentifier = readCtxtId(input);
                            final RequestIdentifier requestIdentifier = readReqId(input);
                            final Reply<?> reply = protocolContext.createReply(input.readObject());
                            readHeaders(input, reply);
                            protocolContext.receiveReply(contextIdentifier, requestIdentifier, reply);
                            return;
                        }
                        case REQUEST: {
                            final ContextIdentifier contextIdentifier = readCtxtId(input);
                            final RequestIdentifier requestIdentifier = readReqId(input);
                            final Request<?> request = protocolContext.createRequest(input.readObject());
                            readHeaders(input, request);
                            if (trace) {
                                log.trace("Received request - body is " + request.getBody().toString());
                            }
                            protocolContext.receiveRequest(contextIdentifier, requestIdentifier, request);
                            return;
                        }
                        case SERVICE_ACTIVATE: {
                            final ServiceIdentifier serviceIdentifier = readSvcId(input);
                            protocolContext.receiveServiceActivate(serviceIdentifier);
                            return;
                        }
                        case SERVICE_REQUEST: {
                            final ServiceIdentifier serviceIdentifier = readSvcId(input);
                            final Class<?> requestType = (Class<?>) input.readObject();
                            final Class<?> replyType = (Class<?>) input.readObject();
                            final String serviceType = input.readUTF();
                            final String serviceGroupName = input.readUTF();
                            final Set<String> interceptors = CollectionUtil.hashSet();
                            int c = input.readInt();
                            for (int i = 0; i < c; i ++) {
                                interceptors.add(input.readUTF());
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
                            final ServiceIdentifier serviceIdentifier = readSvcId(input);
                            protocolContext.receiveServiceTerminate(serviceIdentifier);
                            return;
                        }
                        case STREAM_DATA: {
                            final StreamIdentifier streamIdentifier = readStrId(input);
                            protocolContext.receiveStreamData(streamIdentifier, input);
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
