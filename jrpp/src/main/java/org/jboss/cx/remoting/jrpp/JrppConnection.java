package org.jboss.cx.remoting.jrpp;

import java.io.IOException;
import java.io.ObjectOutput;
import static java.lang.Math.min;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.mina.common.AttributeKey;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoBuffer;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.sasl.SaslClientFilter;
import org.apache.mina.filter.sasl.SaslServerFilter;
import org.apache.mina.handler.multiton.SingleSessionIoHandler;
import org.jboss.cx.remoting.CommonKeys;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.spi.protocol.NumericClientIdentifier;
import org.jboss.cx.remoting.spi.protocol.NumericRequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.NumericServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.NumericStreamIdentifier;
import org.jboss.cx.remoting.jrpp.mina.IoBufferByteMessageInput;
import org.jboss.cx.remoting.jrpp.mina.IoBufferByteMessageOutput;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.util.ObjectMessageInput;
import org.jboss.cx.remoting.util.ObjectMessageOutput;
import org.jboss.cx.remoting.spi.protocol.ClientIdentifier;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.util.WeakHashSet;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

/**
 *
 */
public final class JrppConnection {
    /**
     * The protocol version used by this version of Remoting.  Value is transmitted as an unsigned short.
     */
    private static final int PROTOCOL_VERSION = 0x0000;

    private static final AttributeKey JRPP_CONNECTION = new AttributeKey(JrppConnection.class, "jrppConnection");
    private static final int PING_INTERVAL = 10; // 10 seconds

    public static final String SASL_CLIENT_FILTER_NAME = "SASL client filter";
    public static final String SASL_SERVER_FILTER_NAME = "SASL server filter";

    private final ProtocolHandler protocolHandler;
    private final SingleSessionIoHandler ioHandler;
    private final AttributeMap attributeMap;

    private IoSession ioSession;
    private String remoteName;
    private ProtocolContext protocolContext;
    private IOException failureReason;

    private boolean client;

    private final AtomicInteger streamIdSequence = new AtomicInteger(0);
    private final AtomicInteger contextIdSequence = new AtomicInteger(1);
    private final AtomicInteger serviceIdSequence = new AtomicInteger(0);
    private final AtomicInteger requestIdSequence = new AtomicInteger(0);

    private ClientIdentifier localRootClientIdentifier;
    private ClientIdentifier remoteRootClientIdentifier;

    private final Set<StreamIdentifier> liveStreamSet = CollectionUtil.synchronizedSet(new WeakHashSet<StreamIdentifier>());
    private final Set<ClientIdentifier> liveClientSet = CollectionUtil.synchronizedSet(new WeakHashSet<ClientIdentifier>());
    private final Set<RequestIdentifier> liveRequestSet = CollectionUtil.synchronizedSet(new WeakHashSet<RequestIdentifier>());
    private final Set<ServiceIdentifier> liveServiceSet = CollectionUtil.synchronizedSet(new WeakHashSet<ServiceIdentifier>());

    private int authRetriesLeft = 2;

    /**
     * The negotiated protocol version.  Value is set to {@code min(PROTOCOL_VERSION, remote PROTOCOL_VERSION)}.
     */
    @SuppressWarnings ({"UnusedDeclaration"})
    private int protocolVersion;

    public static SingleSessionIoHandler getHandler(final IoSession session) {
        final JrppConnection connection = (JrppConnection) session.getAttribute(JRPP_CONNECTION);
        return connection.getIoHandler();
    }

    private enum State implements org.jboss.cx.remoting.util.State<State> {
        /** Initial state - unconnected and uninitialized */
        NEW,
        /** Initial state - unconnected but initialized */
        INITIALIZED,
        /** Client side, waiting to receive protocol version info */
        AWAITING_SERVER_VERSION,
        /** Server side, waiting to receive protocol version info */
        AWAITING_CLIENT_VERSION,
        /** Server side, waiting to receive authentication request */
        AWAITING_CLIENT_AUTH_REQUEST,
        /** Client side, auth phase */
        AWAITING_SERVER_CHALLENGE,
        /** Server side, auth phase */
        AWAITING_CLIENT_RESPONSE,
        /** Connection is up */
        UP,
        /** Session is shutting down or closed */
        CLOSED,
        /** Session failed to connect */
        FAILED;

        public boolean isReachable(final State dest) {
            // not perfect but close enough for now
            return compareTo(dest) < 0;
        }
    }

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.NEW);

    private static final Logger log = Logger.getLogger(JrppConnection.class);

    public JrppConnection(final AttributeMap attributeMap) {
        this.attributeMap = attributeMap;
        final Integer retries = attributeMap.get(CommonKeys.AUTH_MAX_RETRIES);
        if (retries != null) {
            final int actualRetries = retries.intValue();
            if (actualRetries < 0) {
                throw new IllegalArgumentException("Value of AUTH_MAX_RETRIES attribute must be greater than or equal to zero");
            }
            authRetriesLeft = actualRetries;
        }
        ioHandler = new IoHandlerImpl();
        protocolHandler = new RemotingProtocolHandler();
    }

    public void initializeClient(final IoSession ioSession) {
        if (ioSession == null) {
            throw new NullPointerException("ioSession is null");
        }
        state.transitionExclusive(State.NEW, State.INITIALIZED);
        try {
            ioSession.setAttribute(JRPP_CONNECTION, this);
            this.ioSession = ioSession;
            client = true;
            remoteRootClientIdentifier = new NumericClientIdentifier(false, 0);
            localRootClientIdentifier = new NumericClientIdentifier(true, 0);
        } finally {
            state.releaseExclusive();
        }
    }

    public void initializeServer(final IoSession ioSession) {
        if (ioSession == null) {
            throw new NullPointerException("ioSession is null");
        }
        state.transitionExclusive(State.NEW, State.INITIALIZED);
        try {
            ioSession.setAttribute(JRPP_CONNECTION, this);
            this.ioSession = ioSession;
            client = false;
            remoteRootClientIdentifier = new NumericClientIdentifier(true, 0);
            localRootClientIdentifier = new NumericClientIdentifier(false, 0);
        } finally {
            state.releaseExclusive();
        }
    }

    public void start(final ProtocolContext protocolContext) {
        if (protocolContext == null) {
            throw new NullPointerException("protocolContext is null");
        }
        state.requireTransitionExclusive(State.INITIALIZED, client ? State.AWAITING_SERVER_VERSION : State.AWAITING_CLIENT_VERSION);
        this.protocolContext = protocolContext;
        state.releaseExclusive();
    }

    private String getNegotiatedMechanism(final String[] clientMechs, final Set<String> serverMechs) throws SaslException {
        for (String name : clientMechs) {
            if (serverMechs.contains(name)) {
                return name;
            }
        }
        throw new SaslException("No acceptable mechanisms found");
    }

    private String getAuthorizationId(final AttributeMap attributeMap, final ProtocolContext protocolContext) {
        final String authorizationId = attributeMap.get(CommonKeys.AUTHORIZATION_ID);
        if (authorizationId != null) {
            return authorizationId;
        }
        return protocolContext.getLocalEndpointName();
    }

    private Set<String> getServerMechanisms(final AttributeMap attributeMap, final Map<String, ?> saslProps) {
        final Set<String> set = attributeMap.get(CommonKeys.SASL_SERVER_MECHANISMS);
        if (set != null) {
            return set;
        }
        final Set<String> mechanisms = new HashSet<String>();
        final Enumeration<SaslServerFactory> e = Sasl.getSaslServerFactories();
        while (e.hasMoreElements()) {
            final SaslServerFactory serverFactory = e.nextElement();
            for (String name : serverFactory.getMechanismNames(saslProps)) {
                mechanisms.add(name);
            }
        }
        return mechanisms;
    }

    private String[] getClientMechanisms(final AttributeMap attributeMap) {
        final List<String> list = attributeMap.get(CommonKeys.SASL_CLIENT_MECHANISMS);
        if (list != null) {
            return list.toArray(new String[list.size()]);
        }
        return new String[] { "SRP" };
    }

    private Map<String, ?> getSaslProperties(final AttributeMap attributeMap) {
        final Map<String, ?> props = attributeMap.get(CommonKeys.SASL_PROPERTIES);
        if (props != null) {
            return props;
        }
        final Map<String, Object> defaultProps = new HashMap<String, Object>();
        defaultProps.put(Sasl.POLICY_NOPLAINTEXT, "true");
        defaultProps.put(Sasl.POLICY_NOANONYMOUS, "true");
        defaultProps.put(Sasl.POLICY_NODICTIONARY, "true");
        defaultProps.put(Sasl.POLICY_NOACTIVE, "true");
        // TODO: JBREM-941
        // defaultProps.put(Sasl.QOP, "auth-conf");
        defaultProps.put("org.jboss.cx.remoting.sasl.srp.verifier", "password");
        return defaultProps;
    }

    private CallbackHandler getServerCallbackHandler(final AttributeMap attributeMap) {
        final CallbackHandler callbackHandler = attributeMap.get(CommonKeys.AUTH_CALLBACK_HANDLER);
        if (callbackHandler != null) {
            return callbackHandler;
        }
        return new CallbackHandler() {
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                for (Callback callback : callbacks) {
                    if (callback instanceof NameCallback) {
                        ((NameCallback)callback).setName("anonymous");
                    } else if (callback instanceof PasswordCallback) {
                        ((PasswordCallback)callback).setPassword("password".toCharArray());
                    } else if (callback instanceof RealmCallback) {
                        continue;
                    } else if (callback instanceof AuthorizeCallback) {
                        ((AuthorizeCallback)callback).setAuthorized(true);
                    } else {
                        throw new UnsupportedCallbackException(callback, "Default anonymous server callback handler cannot support this callback type: " + callback.getClass().getName());
                    }
                }
            }
        };
    }

    private CallbackHandler getClientCallbackHandler(final AttributeMap attributeMap) {
        final CallbackHandler callbackHandler = attributeMap.get(CommonKeys.AUTH_CALLBACK_HANDLER);
        if (callbackHandler != null) {
            return callbackHandler;
        }
        return new CallbackHandler() {
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                for (Callback callback : callbacks) {
                    if (callback instanceof NameCallback) {
                        ((NameCallback)callback).setName("anonymous");
                    } else if (callback instanceof PasswordCallback) {
                        ((PasswordCallback)callback).setPassword("password".toCharArray());
                    } else if (callback instanceof RealmCallback) {
                        ((RealmCallback)callback).setText("default");
                    } else {
                        throw new UnsupportedCallbackException(callback, "Default anonymous client callback handler cannot support this callback type: " + callback.getClass().getName());
                    }
                }
            }
        };
    }

    public static JrppConnection getConnection(IoSession ioSession) {
        return (JrppConnection) ioSession.getAttribute(JRPP_CONNECTION);
    }

    private SaslClientFilter getSaslClientFilter() {
        return (SaslClientFilter) ioSession.getFilterChain().get(SASL_CLIENT_FILTER_NAME);
    }

    private SaslServerFilter getSaslServerFilter() {
        return (SaslServerFilter) ioSession.getFilterChain().get(SASL_SERVER_FILTER_NAME);
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
        output.writeObject(serviceIdentifier);
    }

    private void write(ObjectOutput output, ClientIdentifier clientIdentifier) throws IOException {
        output.writeObject(clientIdentifier);
    }

    private void write(ObjectOutput output, StreamIdentifier streamIdentifier) throws IOException {
        output.writeObject(streamIdentifier);
    }

    private void write(ObjectOutput output, RequestIdentifier requestIdentifier) throws IOException {
        output.writeObject(requestIdentifier);
    }

    public void sendResponse(byte[] rawMsgData) throws IOException {
        final IoBuffer buffer = newBuffer(rawMsgData.length + 100, false);
        final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
        write(output, MessageType.SASL_RESPONSE);
        output.writeInt(rawMsgData.length);
        output.write(rawMsgData);
        output.commit();
    }

    public void sendChallenge(byte[] rawMsgData) throws IOException {
        final IoBuffer buffer = newBuffer(rawMsgData.length + 100, false);
        final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
        write(output, MessageType.SASL_CHALLENGE);
        output.writeInt(rawMsgData.length);
        output.write(rawMsgData);
        output.commit();
    }

    private void sendVersionMessage() throws IOException {
        // send version info
        final IoBuffer buffer = newBuffer(60, false);
        final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
        write(output, MessageType.VERSION);
        output.writeShort(PROTOCOL_VERSION);
        output.writeUTF(protocolContext.getLocalEndpointName());
        output.commit();
    }

    private void sendAuthRequest() throws IOException {
        final CallbackHandler callbackHandler = getClientCallbackHandler(attributeMap);
        final Map<String, ?> saslProps = getSaslProperties(attributeMap);
        final String[] clientMechs = getClientMechanisms(attributeMap);
        final String authorizationId = getAuthorizationId(attributeMap, protocolContext);
        final SaslClient saslClient = Sasl.createSaslClient(clientMechs, authorizationId, "jrpp", remoteName, saslProps, callbackHandler);
        final SaslClientFilter saslClientFilter = getSaslClientFilter();
        saslClientFilter.setSaslClient(ioSession, saslClient);
        final IoBuffer buffer = newBuffer(600, true);
        ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
        write(output, MessageType.AUTH_REQUEST);
        output.writeInt(clientMechs.length);
        for (String mech : clientMechs) {
            output.writeUTF(mech);
        }
        output.commit();
    }

    public void waitForUp() throws IOException {
        try {
            state.waitFor(State.UP);
        } catch (IllegalStateException e) {
            if (state.in(State.FAILED)) {
                throw failureReason;
            } else if (state.in(State.CLOSED)) {
                throw new IOException("Connection closed");
            } else {
                throw e;
            }
        }
    }

    private void close() {
        state.transition(State.CLOSED);
        ioSession.close();
        protocolContext.closeSession();
    }

    private void fail(IOException reason) {
        if (state.transitionExclusive(State.FAILED)) {
            failureReason = reason;
            state.releaseExclusive();
            ioSession.close();
            protocolContext.closeSession();
        }
    }

    private NumericClientIdentifier getNewContextIdentifier() {
        for (;;) {
            final NumericClientIdentifier contextIdentifier = new NumericClientIdentifier(client, contextIdSequence.getAndIncrement());
            if (liveClientSet.add(contextIdentifier)) {
                return contextIdentifier;
            }
        }
    }

    private NumericRequestIdentifier getNewRequestIdentifier() {
        for (;;) {
            final NumericRequestIdentifier requestIdentifier = new NumericRequestIdentifier(client, requestIdSequence.getAndIncrement());
            if (liveRequestSet.add(requestIdentifier)) {
                return requestIdentifier;
            }
        }
    }

    private NumericStreamIdentifier getNewStreamIdentifier() {
        for (;;) {
            final NumericStreamIdentifier streamIdentifier = new NumericStreamIdentifier(client, streamIdSequence.getAndIncrement());
            if (liveStreamSet.add(streamIdentifier)) {
                return streamIdentifier;
            }
        }
    }

    private NumericServiceIdentifier getNewServiceIdentifier() {
        for (;;) {
            final NumericServiceIdentifier serviceIdentifier = new NumericServiceIdentifier(client, serviceIdSequence.getAndIncrement());
            if (liveServiceSet.add(serviceIdentifier)) {
                return serviceIdentifier;
            }
        }
    }

    private static IoBuffer newBuffer(final int initialSize, final boolean autoexpand) {
        return IoBuffer.allocate(initialSize + 4).setAutoExpand(autoexpand).skip(4);
    }

    public final class RemotingProtocolHandler implements ProtocolHandler {
        public void sendClientClosing(final ClientIdentifier remoteClientIdentifier, final boolean done) throws IOException {
            // todo
        }

        public ClientIdentifier openClient(ServiceIdentifier serviceIdentifier) throws IOException {
            final ClientIdentifier clientIdentifier = getNewContextIdentifier();
            final IoBuffer buffer = newBuffer(60, false);
            final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
            write(output, MessageType.OPEN_CONTEXT);
            write(output, serviceIdentifier);
            write(output, clientIdentifier);
            output.commit();
            return clientIdentifier;
        }

        public RequestIdentifier openRequest(ClientIdentifier clientIdentifier) throws IOException {
            return getNewRequestIdentifier();
        }

        public StreamIdentifier openStream() throws IOException {
            return getNewStreamIdentifier();
        }

        public ServiceIdentifier openService() throws IOException {
            return getNewServiceIdentifier();
        }

        public void closeSession() throws IOException {
            if (state.transition(State.CLOSED)) {
                // todo - maybe we don't need to wait?
                ioSession.close().awaitUninterruptibly();
            }
        }

        public String getRemoteEndpointName() {
            return remoteName;
        }

        public void sendServiceClose(ServiceIdentifier serviceIdentifier) throws IOException {
            if (! state.in(State.UP)) {
                return;
            }
            final IoBuffer buffer = newBuffer(60, false);
            final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
            write(output, MessageType.CLOSE_SERVICE);
            write(output, serviceIdentifier);
            output.commit();
        }

        public void sendClientClose(ClientIdentifier clientIdentifier, final boolean immediate, final boolean cancel, final boolean interrupt) throws IOException {
            if (! state.in(State.UP)) {
                return;
            }
            final IoBuffer buffer = newBuffer(300, false);
            final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
            write(output, MessageType.CLOSE_CONTEXT);
            write(output, clientIdentifier);
            output.commit();
        }

        public void closeStream(StreamIdentifier streamIdentifier) throws IOException {
            if (! state.in(State.UP)) {
                return;
            }
            if (true /* todo if close not already sent */) {
                // todo mark as sent or remove from table
                final IoBuffer buffer = newBuffer(60, false);
                final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
                write(output, MessageType.CLOSE_STREAM);
                write(output, streamIdentifier);
                output.commit();
            }
        }

        public void sendReply(ClientIdentifier remoteClientIdentifier, RequestIdentifier requestIdentifier, Object reply) throws IOException {
            if (remoteClientIdentifier == null) {
                throw new NullPointerException("remoteClientIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final IoBuffer buffer = newBuffer(500, true);
            final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
            write(output, MessageType.REPLY);
            write(output, remoteClientIdentifier);
            write(output, requestIdentifier);
            output.writeObject(reply);
            output.commit();
        }

        public void sendException(ClientIdentifier remoteClientIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) throws IOException {
            if (remoteClientIdentifier == null) {
                throw new NullPointerException("remoteClientIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            if (exception == null) {
                throw new NullPointerException("exception is null");
            }
            final IoBuffer buffer = newBuffer(500, true);
            final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
            write(output, MessageType.EXCEPTION);
            write(output, remoteClientIdentifier);
            write(output, requestIdentifier);
            output.writeObject(exception);
            output.commit();
        }

        public void sendRequest(ClientIdentifier clientIdentifier, RequestIdentifier requestIdentifier, Object request, final Executor streamExecutor) throws IOException {
            if (clientIdentifier == null) {
                throw new NullPointerException("clientIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final IoBuffer buffer = newBuffer(500, true);
            final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession), streamExecutor);
            write(output, MessageType.REQUEST);
            write(output, clientIdentifier);
            write(output, requestIdentifier);
            output.writeObject(request);
            output.commit();
        }

        public void sendCancelAcknowledge(ClientIdentifier remoteClientIdentifier, RequestIdentifier requestIdentifier) throws IOException {
            if (remoteClientIdentifier == null) {
                throw new NullPointerException("remoteClientIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final IoBuffer buffer = newBuffer(60, false);
            final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
            write(output, MessageType.CANCEL_ACK);
            write(output, remoteClientIdentifier);
            write(output, requestIdentifier);
            output.commit();
        }

        public void sendServiceClosing(ServiceIdentifier remoteServiceIdentifier) throws IOException {
            if (remoteServiceIdentifier == null) {
                throw new NullPointerException("remoteServiceIdentifier is null");
            }
            final IoBuffer buffer = newBuffer(60, false);
            final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
            write(output, MessageType.SERVICE_TERMINATE);
            write(output, remoteServiceIdentifier);
            output.commit();
        }

        public ClientIdentifier getLocalRootClientIdentifier() {
            return localRootClientIdentifier;
        }

        public ClientIdentifier getRemoteRootClientIdentifier() {
            return remoteRootClientIdentifier;
        }

        public void sendCancelRequest(ClientIdentifier clientIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) throws IOException {
            if (clientIdentifier == null) {
                throw new NullPointerException("clientIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final IoBuffer buffer = newBuffer(60, false);
            final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
            write(output, MessageType.CANCEL_REQ);
            write(output, clientIdentifier);
            write(output, requestIdentifier);
            output.writeBoolean(mayInterrupt);
            output.commit();
        }

        public ClientIdentifier openClient() throws IOException {
            return null;
        }

        public ObjectMessageOutput sendStreamData(StreamIdentifier streamIdentifier, Executor streamExecutor) throws IOException {
            if (streamIdentifier == null) {
                throw new NullPointerException("streamIdentifier is null");
            }
            if (streamExecutor == null) {
                throw new NullPointerException("streamExeceutor is null");
            }
            final IoBuffer buffer = newBuffer(500, true);
            final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession), streamExecutor);
            write(output, MessageType.STREAM_DATA);
            write(output, streamIdentifier);
            return output;
        }
    }

    private final class IoHandlerImpl implements SingleSessionIoHandler {
        public void sessionCreated() throws IOException {
            // Send a ping message every PING_INTERVAL seconds
            // if there's nothing to send.
            ioSession.getConfig().setWriterIdleTime(PING_INTERVAL);
            // Close the session if no ping message is received
            // within (PING_INTERVAL * 2) seconds.
            ioSession.getConfig().setReaderIdleTime(PING_INTERVAL * 2);
            sendVersionMessage();
        }

        public void sessionOpened() throws IOException {
            // Nothing to do here.
        }

        public void sessionClosed() {
            State current = state.getStateExclusive();
            try {
                switch (current) {
                    case AWAITING_CLIENT_AUTH_REQUEST:
                    case AWAITING_CLIENT_RESPONSE:
                    case AWAITING_CLIENT_VERSION:
                    case AWAITING_SERVER_CHALLENGE:
                    case AWAITING_SERVER_VERSION:
                    case NEW:
                        fail(new IOException("Unexpected session close"));
                        return;
                    default:
                        close();
                        return;
                }
            } finally {
                state.releaseExclusive();
            }
        }

        public void sessionIdle(IdleStatus idleStatus) throws IOException {
            if (idleStatus == IdleStatus.WRITER_IDLE) {
                // Send a ping message before the remote peer closes the
                // connection.
                final IoBuffer buffer = newBuffer(60, false);
                final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
                write(output, MessageType.PING);
                output.commit();
            } else if (idleStatus == IdleStatus.READER_IDLE) {
                // No message since last communication - close the connection.
                close();
            }
        }

        public void exceptionCaught(Throwable throwable) {
            log.error(throwable, "Exception from JRPP connection handler");
            close();
            if (throwable instanceof IOException) {
                fail((IOException)throwable);
            } else {
                fail(new IOException("Unexpected exception from handler: " + throwable.toString()));
            }
        }

        public void messageReceived(Object message) throws Exception {
            final ObjectMessageInput input = protocolContext.getMessageInput(new IoBufferByteMessageInput((IoBuffer) message));
            handleMessage(input);
        }

        private void handleMessage(final ObjectMessageInput input) throws Exception {
            final boolean trace = log.isTrace();
            final MessageType type = MessageType.values()[input.readByte() & 0xff];
            if (trace) {
                log.trace("Received message of type %s in state %s", type, state.getState());
            }
            OUT: switch (state.getState()) {
                case AWAITING_CLIENT_VERSION: {
                    switch (type) {
                        case VERSION: {
                            protocolVersion = min(input.readShort() & 0xffff, PROTOCOL_VERSION);
                            if (trace) {
                                log.trace("Server negotiated protocol version " + protocolVersion);
                            }
                            final String name = input.readUTF();
                            remoteName = name.length() > 0 ? name : null;
                            state.requireTransition(State.AWAITING_CLIENT_AUTH_REQUEST);
                            return;
                        }
                        case PING: {
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
                            int len = input.readInt();
                            byte[] bytes = new byte[len];
                            input.readFully(bytes);
                            SaslServerFilter saslServerFilter = getSaslServerFilter();
                            try {
                                if (saslServerFilter.handleSaslResponse(ioSession, bytes)) {
                                    final IoBuffer buffer = newBuffer(60, false);
                                    final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
                                    write(output, MessageType.AUTH_SUCCESS);
                                    output.commit();
                                    saslServerFilter.startEncryption(ioSession);
                                    log.trace("Server transitioning to UP state");
                                    state.requireTransition(State.AWAITING_CLIENT_RESPONSE, State.UP);
                                    protocolContext.receiveRemoteSideReady(remoteName);
                                }
                            } catch (SaslException ex) {
                                final IoBuffer buffer = newBuffer(100, true);
                                final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
                                write(output, MessageType.AUTH_FAILED);
                                output.writeUTF("Authentication failed: " + ex.getMessage());
                                output.commit();
                                log.debug("Client authentication failed (" + ex.getMessage() + ")");
                                log.trace(ex, "Client authentication failure details follow");
                                // todo - retry counter - JBREM-907
                                state.requireTransition(State.AWAITING_CLIENT_RESPONSE, State.AWAITING_CLIENT_AUTH_REQUEST);
                            }
                            return;
                        }
                        case AUTH_REQUEST: {
                            state.transition(State.AWAITING_CLIENT_AUTH_REQUEST);
                            break; // fall thru to AWAITING_CLIENT_AUTH_REQUEST/AUTH_REQUEST
                        }
                        case PING: {
                            return;
                        }
                        default: break OUT;
                    }
                }
                case AWAITING_CLIENT_AUTH_REQUEST: {
                    switch (type) {
                        case AUTH_REQUEST: {
                            final int mechCount = input.readInt();
                            final String[] clientMechs = new String[mechCount];
                            for (int i = 0; i < mechCount; i ++) {
                                clientMechs[i] = input.readUTF();
                            }
                            try {
                                final CallbackHandler callbackHandler = getServerCallbackHandler(attributeMap);
                                final Map<String, ?> saslProps = getSaslProperties(attributeMap);
                                final Set<String> serverMechs = getServerMechanisms(attributeMap, saslProps);
                                final String negotiatedMechanism = getNegotiatedMechanism(clientMechs, serverMechs);
                                final SaslServer saslServer = Sasl.createSaslServer(negotiatedMechanism, "jrpp", protocolContext.getLocalEndpointName(), saslProps, callbackHandler);
                                final SaslServerFilter saslServerFilter = getSaslServerFilter();
                                saslServerFilter.setSaslServer(ioSession, saslServer);
                                if (saslServerFilter.sendInitialChallenge(ioSession)) {
                                    // complete (that was quick!)
                                    final IoBuffer buffer = newBuffer(60, false);
                                    final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
                                    write(output, MessageType.AUTH_SUCCESS);
                                    output.commit();
                                    log.trace("Server transitioning to UP state (immediate)");
                                    state.requireTransition(State.UP);
                                    protocolContext.receiveRemoteSideReady(remoteName);
                                } else {
                                    state.requireTransition(State.AWAITING_CLIENT_RESPONSE);
                                }
                            } catch (SaslException ex) {
                                final IoBuffer buffer = newBuffer(100, true);
                                final ObjectMessageOutput output = protocolContext.getMessageOutput(new IoBufferByteMessageOutput(buffer, ioSession));
                                write(output, MessageType.AUTH_FAILED);
                                output.writeUTF("Unable to initiate SASL authentication: " + ex.getMessage());
                                output.commit();
                                if (authRetriesLeft == 0) {
                                    close();
                                }
                                authRetriesLeft--;
                            }
                            return;
                        }
                        case PING: {
                            return;
                        }
                        default: break OUT;
                    }
                }
                case AWAITING_SERVER_VERSION: {
                    switch (type) {
                        case VERSION: {
                            protocolVersion = min(input.readShort() & 0xffff, PROTOCOL_VERSION);
                            if (trace) {
                                log.trace("Client negotiated protocol version " + protocolVersion);
                            }
                            final String name = input.readUTF();
                            remoteName = name.length() > 0 ? name : null;
                            sendAuthRequest();
                            state.requireTransition(State.AWAITING_SERVER_CHALLENGE);
                            return;
                        }
                        case PING: {
                            return;
                        }
                        default: break OUT;
                    }
                }
                case AWAITING_SERVER_CHALLENGE: {
                    switch (type) {
                        case SASL_CHALLENGE: {
                            int len = input.readInt();
                            byte[] bytes = new byte[len];
                            input.readFully(bytes);
                            SaslClientFilter saslClientFilter = getSaslClientFilter();
                            try {
                                saslClientFilter.handleSaslChallenge(ioSession, bytes);
                            } catch (SaslException ex) {
                                log.debug("Failed to handle challenge from server (%s).  Sending new auth request.", ex.getMessage());
                                // todo - retry counter - JBREM-907
                                sendAuthRequest();
                            }
                            return;
                        }
                        case AUTH_SUCCESS: {
                            SaslClientFilter saslClientFilter = getSaslClientFilter();
                            saslClientFilter.startEncryption(ioSession);
                            log.trace("Client transitioning to UP state");
                            state.requireTransition(State.AWAITING_SERVER_CHALLENGE, State.UP);
                            protocolContext.receiveRemoteSideReady(remoteName);
                            return;
                        }
                        case AUTH_FAILED: {
                            String reason = input.readUTF();
                            log.debug("JRPP client failed to authenticate: %s", reason);
                            final SaslClientFilter oldClientFilter = getSaslClientFilter();
                            oldClientFilter.destroy();
                            if (authRetriesLeft == 0) {
                                close();
                                return;
                            }
                            authRetriesLeft--;
                            final CallbackHandler callbackHandler = getClientCallbackHandler(attributeMap);
                            final Map<String, ?> saslProps = getSaslProperties(attributeMap);
                            final String[] clientMechs = getClientMechanisms(attributeMap);
                            final String authorizationId = getAuthorizationId(attributeMap, protocolContext);
                            final SaslClient saslClient = Sasl.createSaslClient(clientMechs, authorizationId, "jrpp", remoteName, saslProps, callbackHandler);
                            final SaslClientFilter saslClientFilter = getSaslClientFilter();
                            saslClientFilter.setSaslClient(ioSession, saslClient);
                            sendAuthRequest();
                            return;
                        }
                        case PING: {
                            return;
                        }
                        default: break OUT;
                    }
                }
                case UP: {
                    switch (type) {
                        case OPEN_CONTEXT: {
                            final ServiceIdentifier serviceIdentifier = (ServiceIdentifier) input.readObject();
                            final ClientIdentifier clientIdentifier = (ClientIdentifier) input.readObject();
                            protocolContext.receiveOpenedContext(serviceIdentifier, clientIdentifier);
                            return;
                        }
                        case CANCEL_ACK: {
                            final ClientIdentifier clientIdentifier = (ClientIdentifier) input.readObject();
                            final RequestIdentifier requestIdentifier = (RequestIdentifier) input.readObject();
                            protocolContext.receiveCancelAcknowledge(clientIdentifier, requestIdentifier);
                            return;
                        }
                        case CANCEL_REQ: {
                            final ClientIdentifier clientIdentifier = (ClientIdentifier) input.readObject();
                            final RequestIdentifier requestIdentifier = (RequestIdentifier) input.readObject();
                            final boolean mayInterrupt = input.readBoolean();
                            protocolContext.receiveCancelRequest(clientIdentifier, requestIdentifier, mayInterrupt);
                            return;
                        }
                        case CLOSE_CONTEXT: {
                            final ClientIdentifier clientIdentifier = (ClientIdentifier) input.readObject();
                            protocolContext.receiveClientClose(clientIdentifier, false, false, false);
                            return;
                        }
                        case CLOSE_SERVICE: {
                            final ServiceIdentifier serviceIdentifier = (ServiceIdentifier) input.readObject();
                            protocolContext.receiveServiceClose(serviceIdentifier);
                            return;
                        }
                        case CLOSE_STREAM: {
                            final StreamIdentifier streamIdentifier = (StreamIdentifier) input.readObject();
                            protocolContext.closeStream(streamIdentifier);
                            return;
                        }
                        case EXCEPTION: {
                            final ClientIdentifier clientIdentifier = (ClientIdentifier) input.readObject();
                            final RequestIdentifier requestIdentifier = (RequestIdentifier) input.readObject();
                            final RemoteExecutionException exception = (RemoteExecutionException) input.readObject();
                            protocolContext.receiveException(clientIdentifier, requestIdentifier, exception);
                            return;
                        }
                        case REPLY: {
                            final ClientIdentifier clientIdentifier = (ClientIdentifier) input.readObject();
                            final RequestIdentifier requestIdentifier = (RequestIdentifier) input.readObject();
                            final Object reply = input.readObject();
                            protocolContext.receiveReply(clientIdentifier, requestIdentifier, reply);
                            return;
                        }
                        case REQUEST: {
                            final ClientIdentifier clientIdentifier = (ClientIdentifier) input.readObject();
                            final RequestIdentifier requestIdentifier = (RequestIdentifier) input.readObject();
                            final Object request = input.readObject();
                            if (trace) {
                                log.trace("Received request - body is %s", request);
                            }
                            protocolContext.receiveRequest(clientIdentifier, requestIdentifier, request);
                            return;
                        }
                       case SERVICE_TERMINATE: {
                            final ServiceIdentifier serviceIdentifier = (ServiceIdentifier) input.readObject();
                            protocolContext.receiveServiceClosing(serviceIdentifier);
                            return;
                        }
                        case STREAM_DATA: {
                            final StreamIdentifier streamIdentifier = (StreamIdentifier) input.readObject();
                            protocolContext.receiveStreamData(streamIdentifier, input);
                            return;
                        }
                        case PING: {
                            return;
                        }
                        default: break OUT;
                    }
                }
            }
            throw new IllegalStateException("Got message " + type + " during " + state);
        }

        public void messageSent(Object object) {
            // Nothing to do.
        }
    }

    /**
     * Keep elements in order.  If an element is to be deleted, replace it with a placeholder.
     */
    private enum MessageType {
        VERSION,
        AUTH_REQUEST,
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
        SERVICE_TERMINATE,
        STREAM_DATA,
        PING,
    }
}
