package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.core.stream.DefaultStreamDetector;
import org.jboss.xnio.log.Logger;
import org.jboss.cx.remoting.util.ByteMessageInput;
import org.jboss.cx.remoting.util.ByteMessageOutput;
import org.jboss.cx.remoting.util.ObjectMessageInput;
import org.jboss.cx.remoting.util.ObjectMessageOutput;
import org.jboss.cx.remoting.spi.stream.StreamDetector;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.cx.remoting.spi.marshal.MarshallerFactory;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.util.CollectionUtil;

/**
 * Three execution contexts:
 *
 * - Inbound protocol handler - controlled by server/network handler
 * - Context client context - controlled by user/container
 * - Local work handler - ExecutorService provided to Endpoint
 */
public final class CoreSession {
    private static final Logger log = Logger.getLogger(CoreSession.class);

    private final ProtocolContextImpl protocolContext = new ProtocolContextImpl();
    private final UserSession userSession = new UserSession();

    // stream serialization detectors - immutable (for now?)
    private final List<StreamDetector> streamDetectors;
    private List<ObjectResolver> resolvers = new ArrayList<ObjectResolver>();
    private MarshallerFactory marshallerFactory;

    // Contexts and services that are available on the remote end of this session
    // In these paris, the Server points to the ProtocolHandler, and the Client points to...whatever
    private final ConcurrentMap<ClientIdentifier, ClientContextPair> clientContexts = CollectionUtil.concurrentMap();
    private final ConcurrentMap<ServiceIdentifier, ClientServicePair> clientServices = CollectionUtil.concurrentMap();

    // Contexts and services that are available on this end of this session
    // In these pairs, the Client points to the ProtocolHandler, and the Server points to... whatever
    private final ConcurrentMap<ClientIdentifier, ServerContextPair> serverContexts = CollectionUtil.concurrentMap();
    private final ConcurrentMap<ServiceIdentifier, ServerServicePair> serverServices = CollectionUtil.concurrentMap();

    // streams - strong references, only clean up if a close message is sent or received
    private final ConcurrentMap<StreamIdentifier, CoreStream> streams = CollectionUtil.concurrentMap();

    // don't GC the endpoint while a session lives
    private final CoreEndpoint endpoint;
    private final Executor executor;
    private final Set<CloseHandler<Session>> closeHandlers = CollectionUtil.synchronizedSet(new LinkedHashSet<CloseHandler<Session>>());

    /** The protocol handler.  Set on NEW -> CONNECTING */
    private ProtocolHandler protocolHandler;
    /** The remote endpoint name.  Set on CONNECTING -> UP */
    private String remoteEndpointName;
    /** The root context.  Set on CONNECTING -> UP */
    private Client<?, ?> rootClient;

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.NEW);
    private ObjectResolver resolver; // todo - initialize to a composite resolver

    // Constructors

    CoreSession(final CoreEndpoint endpoint) {
        if (endpoint == null) {
            throw new NullPointerException("endpoint is null");
        }
        this.endpoint = endpoint;
        executor = endpoint.getExecutor();
        // todo - make stream detectors pluggable
        streamDetectors = java.util.Collections.singletonList(DefaultStreamDetector.INSTANCE);
    }

    UserSession getUserSession() {
        state.waitForHold(State.UP);
        try {
            return userSession;
        } finally {
            state.release();
        }
    }

    public MarshallerFactory getMarshallerFactory() {
        return marshallerFactory;
    }

    public void setMarshallerFactory(final MarshallerFactory marshallerFactory) {
        this.marshallerFactory = marshallerFactory;
    }

    public void addFirstResolver(ObjectResolver resolver) {
        resolvers.add(0, resolver);
    }

    public void addLastResolver(ObjectResolver resolver) {
        resolvers.add(resolver);
    }


    // Initializers

    private <I, O> void doInitialize(final ProtocolHandler protocolHandler, final Client<I, O> rootClient) {
        if (protocolHandler == null) {
            throw new NullPointerException("protocolHandler is null");
        }
        this.protocolHandler = protocolHandler;
        if (rootClient instanceof AbstractRealClient) {
            final AbstractRealClient<I, O> abstractRealContext = (AbstractRealClient<I, O>) rootClient;
            // Forward local context
            final ClientIdentifier localIdentifier = protocolHandler.getLocalRootClientIdentifier();
            if (localIdentifier == null) {
                throw new NullPointerException("localIdentifier is null");
            }
            final ProtocolClientInitiatorImpl<I, O> contextClient = new ProtocolClientInitiatorImpl<I, O>(localIdentifier);
            serverContexts.put(localIdentifier, new ServerContextPair<I, O>(contextClient, abstractRealContext.getContextServer()));
            log.trace("Initialized session with local context %s", localIdentifier);
        }
        // Forward remote context
        final ClientIdentifier remoteIdentifier = protocolHandler.getRemoteRootClientIdentifier();
        if (remoteIdentifier == null) {
            throw new NullPointerException("remoteIdentifier is null");
        }
        final ProtocolClientResponderImpl<I, O> contextServer = new ProtocolClientResponderImpl<I,O>(remoteIdentifier);
        final CoreOutboundClient<I, O> coreOutboundClient = new CoreOutboundClient<I, O>(executor);
        clientContexts.put(remoteIdentifier, new ClientContextPair<I, O>(coreOutboundClient.getClientInitiator(), contextServer, remoteIdentifier));
        coreOutboundClient.initialize(contextServer);
        this.rootClient = coreOutboundClient.getUserContext();
        log.trace("Initialized session with remote context %s", remoteIdentifier);
    }

    <I, O> void initializeServer(final ProtocolHandler protocolHandler, final Client<I, O> rootClient) {
        if (protocolHandler == null) {
            throw new NullPointerException("protocolHandler is null");
        }
        boolean ok = false;
        state.requireTransitionExclusive(State.NEW, State.CONNECTING);
        try {
            doInitialize(protocolHandler, rootClient);
            ok = true;
        } finally {
            state.releaseExclusive();
            if (! ok) {
                state.transition(State.DOWN);
            }
        }
    }

    <I, O> void initializeClient(final ProtocolHandlerFactory protocolHandlerFactory, final URI remoteUri, final AttributeMap attributeMap, final Client<I, O> rootClient) throws IOException {
        if (protocolHandlerFactory == null) {
            throw new NullPointerException("protocolHandlerFactory is null");
        }
        boolean ok = false;
        state.requireTransitionExclusive(State.NEW, State.CONNECTING);
        try {
            doInitialize(protocolHandlerFactory.createHandler(protocolContext, remoteUri, attributeMap), rootClient);
            ok = true;
        } finally {
            state.releaseExclusive();
            if (! ok) {
                state.transition(State.DOWN);
            }
        }
    }

    public ProtocolContext getProtocolContext() {
        return protocolContext;
    }

    public ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    // State mgmt

    private enum State implements org.jboss.cx.remoting.util.State<State> {
        NEW,
        CONNECTING,
        UP,
        STOPPING,
        DOWN;

        public boolean isReachable(final State dest) {
            return compareTo(dest) < 0;
        }
    }

    // Client mgmt

    // User session impl

    public final class UserSession implements Session {
        private UserSession() {}

        private final ConcurrentMap<Object, Object> sessionMap = CollectionUtil.concurrentMap();

        public void close() throws RemotingException {
            // todo - maybe drain the session first?
            shutdown();
            state.waitFor(State.DOWN);
        }

        public void addCloseHandler(final CloseHandler<Session> closeHandler) {
            final State current = state.getStateHold();
            try {
                switch (current) {
                    case DOWN:
                    case STOPPING:
                        closeHandler.handleClose(this);
                        break;
                    default:
                        closeHandlers.add(closeHandler);
                }
            } finally {
                state.release();
            }
        }

        public ConcurrentMap<Object, Object> getAttributes() {
            return sessionMap;
        }

        public String getLocalEndpointName() {
            return endpoint.getName();
        }

        public String getRemoteEndpointName() {
            return remoteEndpointName;
        }

        @SuppressWarnings ({"unchecked"})
        public <I, O> Client<I, O> getRootClient() {
            return (Client<I, O>) rootClient;
        }
    }

    // Protocol context

    @SuppressWarnings ({"unchecked"})
    private static <O> void doSendReply(RequestInitiator<O> requestInitiator, Object data) throws RemotingException {
        requestInitiator.handleReply((O)data);
    }

    // Lifecycle

    private void shutdown() {
        if (state.transition(State.UP, State.STOPPING)) {
            for (final CloseHandler<Session> closeHandler : closeHandlers) {
                executor.execute(new Runnable() {
                    public void run() {
                        closeHandler.handleClose(userSession);
                    }
                });
            }
            closeHandlers.clear();
            try {
                log.trace("Initiating session shutdown");
                protocolHandler.closeSession();
            } catch (IOException e) {
                log.trace(e, "Protocol handler session close failed");
            } finally {
                endpoint.removeSession(this);
            }
        }
    }

    public final class ProtocolContextImpl implements ProtocolContext {

        public void closeSession() {
            shutdown();
            if (state.transition(State.STOPPING, State.DOWN)) {
                log.trace("Session shut down");
            }
        }

        public ObjectMessageOutput getMessageOutput(ByteMessageOutput target) throws IOException {
            if (target == null) {
                throw new NullPointerException("target is null");
            }
            return marshallerFactory.createMarshaller(resolver, getClass().getClassLoader() /* todo this is WRONG */).getMarshalingSink(target);
        }

        public ObjectMessageOutput getMessageOutput(ByteMessageOutput target, Executor streamExecutor) throws IOException {
            if (target == null) {
                throw new NullPointerException("target is null");
            }
            if (streamExecutor == null) {
                throw new NullPointerException("streamExecutor is null");
            }
            return marshallerFactory.createMarshaller(resolver, getClass().getClassLoader() /* todo this is WRONG */).getMarshalingSink(target);
        }

        public ObjectMessageInput getMessageInput(ByteMessageInput source) throws IOException {
            if (source == null) {
                throw new NullPointerException("source is null");
            }
            return marshallerFactory.createMarshaller(resolver, getClass().getClassLoader() /* todo this is WRONG */).getUnmarshalingSource(source);
        }

        public String getLocalEndpointName() {
            return endpoint.getName();
        }

        public void receiveClientClose(ClientIdentifier remoteClientIdentifier, final boolean immediate, final boolean cancel, final boolean interrupt) {
            if (remoteClientIdentifier == null) {
                throw new NullPointerException("remoteClientIdentifier is null");
            }
            final ServerContextPair contextPair = serverContexts.remove(remoteClientIdentifier);
            // todo - do the whole close operation
            try {
                contextPair.clientResponder.handleClose(immediate, cancel);
            } catch (RemotingException e) {
                log.trace(e, "Failed to forward a context close");
            }
        }

        public void closeStream(StreamIdentifier streamIdentifier) {
            if (streamIdentifier == null) {
                throw new NullPointerException("streamIdentifier is null");
            }
            final CoreStream coreStream = streams.remove(streamIdentifier);
            try {
                coreStream.getStreamSerializer().handleClose();
            } catch (IOException e) {
                log.trace(e, "Failed to close the stream");
            }
        }

        public void receiveServiceClose(ServiceIdentifier serviceIdentifier) {
            if (serviceIdentifier == null) {
                throw new NullPointerException("serviceIdentifier is null");
            }
            final ServerServicePair servicePair = serverServices.remove(serviceIdentifier);
            try {
                servicePair.serviceResponder.handleClose();
            } catch (RemotingException e) {
                log.trace(e, "Failed to forward a service close");
            }
        }

        @SuppressWarnings ({"unchecked"})
        public void receiveOpenedContext(ServiceIdentifier remoteServiceIdentifier, ClientIdentifier remoteClientIdentifier) {
            if (remoteServiceIdentifier == null) {
                throw new NullPointerException("remoteServiceIdentifier is null");
            }
            if (remoteClientIdentifier == null) {
                throw new NullPointerException("remoteClientIdentifier is null");
            }
            try {
                // This operation needs to be idempotent
                if (! serverContexts.containsKey(remoteClientIdentifier)) {
                    final ServerServicePair servicePair = serverServices.get(remoteServiceIdentifier);
                    final ProtocolClientInitiatorImpl contextClient = new ProtocolClientInitiatorImpl(remoteClientIdentifier);
                    final ClientResponder clientResponder = servicePair.serviceResponder.createNewClient(contextClient);
                    if (serverContexts.putIfAbsent(remoteClientIdentifier, new ServerContextPair(contextClient, clientResponder)) != null) {
                        clientResponder.handleClose(true, true);
                    }
                }
            } catch (RemotingException e) {
                log.trace(e, "Failed to add a context to a service");
            }
        }

        public void receiveServiceClosing(ServiceIdentifier serviceIdentifier) {
            if (serviceIdentifier == null) {
                throw new NullPointerException("serviceIdentifier is null");
            }
            final ClientServicePair servicePair = clientServices.get(serviceIdentifier);
            try {
                servicePair.serviceInitiator.handleClosing();
            } catch (RemotingException e) {
                log.trace(e, "Failed to signal that a service was closing on the remote side");
            }
        }

        public void receiveClientClosing(ClientIdentifier clientIdentifier, boolean done) {
            if (clientIdentifier == null) {
                throw new NullPointerException("clientIdentifier is null");
            }
            final ClientContextPair contextPair = clientContexts.get(clientIdentifier);
            try {
                contextPair.clientInitiator.handleClosing(done);
            } catch (RemotingException e) {
                log.trace(e, "Failed to signal that a context was closing on the remote side");
            }
        }

        public void receiveReply(ClientIdentifier clientIdentifier, RequestIdentifier requestIdentifier, Object reply) {
            if (clientIdentifier == null) {
                throw new NullPointerException("clientIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final ClientContextPair contextPair = clientContexts.get(clientIdentifier);
            if (contextPair == null) {
                log.trace("Got reply for request %s on unknown context %s", requestIdentifier, clientIdentifier);
            } else {
                final ProtocolClientResponderImpl<?, ?> contextServer = contextPair.contextServerRef.get();
                if (contextServer == null) {
                    log.trace("Got reply for request %s on unknown recently leaked context %s", requestIdentifier, clientIdentifier);
                } else {
                    final RequestInitiator<?> requestInitiator = (RequestInitiator<?>) contextServer.requests.get(requestIdentifier);
                    if (requestInitiator == null) {
                        log.trace("Got reply for unknown request %s on context %s", requestIdentifier, clientIdentifier);
                    } else try {
                        doSendReply(requestInitiator, reply);
                    } catch (RemotingException e) {
                        log.trace(e, "Failed to receive a reply");
                    }
                }
            }
        }

        public void receiveException(ClientIdentifier clientIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) {
            if (clientIdentifier == null) {
                throw new NullPointerException("clientIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            if (exception == null) {
                throw new NullPointerException("exception is null");
            }
            final ClientContextPair contextPair = clientContexts.get(clientIdentifier);
            if (contextPair == null) {
                log.trace("Got exception reply for request %s on unknown context %s", requestIdentifier, clientIdentifier);
            } else {
                final ProtocolClientResponderImpl<?, ?> contextServer = contextPair.contextServerRef.get();
                if (contextServer == null) {
                    log.trace("Got exception reply for request %s on unknown recently leaked context %s", requestIdentifier, clientIdentifier);
                } else {
                    final RequestInitiator<?> requestInitiator = (RequestInitiator<?>) contextServer.requests.get(requestIdentifier);
                    if (requestInitiator == null) {
                        log.trace("Got exception reply for unknown request %s on context %s", requestIdentifier, clientIdentifier);
                    } else try {
                        requestInitiator.handleException(exception);
                    } catch (RemotingException e) {
                        log.trace(e, "Failed to receive an exception reply");
                    }
                }
            }
        }

        public void receiveCancelAcknowledge(ClientIdentifier clientIdentifier, RequestIdentifier requestIdentifier) {
            if (clientIdentifier == null) {
                throw new NullPointerException("clientIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final ClientContextPair contextPair = clientContexts.get(clientIdentifier);
            if (contextPair == null) {
                log.trace("Got cancellation acknowledgement for request %s on unknown context %s", requestIdentifier, clientIdentifier);
            } else {
                final ProtocolClientResponderImpl<?, ?> contextServer = contextPair.contextServerRef.get();
                if (contextServer == null) {
                    log.trace("Got cancellation acknowledgement for request %s on unknown recently leaked context %s", requestIdentifier, clientIdentifier);
                } else {
                    final RequestInitiator<?> requestInitiator = (RequestInitiator<?>) contextServer.requests.get(requestIdentifier);
                    if (requestInitiator == null) {
                        log.trace("Got cancellation acknowledgement for unknown request %s on context %s", requestIdentifier, clientIdentifier);
                    } else try {
                        requestInitiator.handleCancelAcknowledge();
                    } catch (RemotingException e) {
                        log.trace(e, "Failed to receive a cancellation acknowledgement");
                    }
                }
            }
        }

        public void receiveCancelRequest(ClientIdentifier remoteClientIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) {
            if (remoteClientIdentifier == null) {
                throw new NullPointerException("remoteClientIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final ServerContextPair contextPair = serverContexts.get(remoteClientIdentifier);
            final RequestResponder<?> requestResponder = (RequestResponder<?>) contextPair.contextClient.requests.get(requestIdentifier);
            try {
                requestResponder.handleCancelRequest(mayInterrupt);
            } catch (RemotingException e) {
                log.trace(e, "Failed to receive a cancellation request");
            }
        }

        public void receiveStreamData(StreamIdentifier streamIdentifier, ObjectMessageInput data) {
            if (streamIdentifier == null) {
                throw new NullPointerException("streamIdentifier is null");
            }
            if (data == null) {
                throw new NullPointerException("data is null");
            }
            final CoreStream coreStream = streams.get(streamIdentifier);
            if (coreStream == null) {
                log.trace("Received stream data on an unknown context %s", streamIdentifier);
            } else {
                coreStream.receiveStreamData(data);
            }
        }

        @SuppressWarnings ({"unchecked"})
        public void receiveRemoteSideReady(String remoteEndpointName) {
            state.waitFor(State.CONNECTING);
            state.requireTransitionExclusive(State.CONNECTING, State.UP);
            try {
                CoreSession.this.remoteEndpointName = remoteEndpointName;
            } finally {
                state.releaseExclusive();
            }
        }

        @SuppressWarnings ({"unchecked"})
        public void receiveRequest(final ClientIdentifier remoteClientIdentifier, final RequestIdentifier requestIdentifier, final Object request) {
            if (remoteClientIdentifier == null) {
                throw new NullPointerException("remoteClientIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final ServerContextPair contextPair = serverContexts.get(remoteClientIdentifier);
            if (contextPair == null) {
                log.trace("Received a request on an unknown context %s", remoteClientIdentifier);
                return;
            }
            try {
                final RequestInitiator requestInitiator = contextPair.contextClient.addClient(requestIdentifier);
                final RequestResponder requestResponder = contextPair.clientResponder.createNewRequest(requestInitiator);
                requestResponder.handleRequest(request, executor);
            } catch (RemotingException e) {
                e.printStackTrace();
            }
        }
    }

    private final class WeakProtocolContextServerReference<I, O> extends WeakReference<ProtocolClientResponderImpl<I, O>> {
        private final ClientContextPair<I, O> contextPair;

        private WeakProtocolContextServerReference(ProtocolClientResponderImpl<I, O> referent, ClientContextPair<I, O> contextPair) {
            super(referent);
            this.contextPair = contextPair;
        }

        public ProtocolClientResponderImpl<I, O> get() {
            return super.get();
        }

        public boolean enqueue() {
            try {
                clientContexts.remove(contextPair.clientIdentifier, contextPair);
                // todo close?
            } finally {
                return super.enqueue();
            }
        }
    }

    private final class ClientContextPair<I, O> {
        private final ClientInitiator clientInitiator;
        private final WeakProtocolContextServerReference<I, O> contextServerRef;
        private final ClientIdentifier clientIdentifier;

        private ClientContextPair(final ClientInitiator clientInitiator, final ProtocolClientResponderImpl<I, O> contextServer, final ClientIdentifier clientIdentifier) {
            this.clientInitiator = clientInitiator;
            this.clientIdentifier = clientIdentifier;
            contextServerRef = new WeakProtocolContextServerReference<I, O>(contextServer, this);
            // todo - auto-cleanup
        }
    }

    private static final class ServerContextPair<I, O> {
        private final ProtocolClientInitiatorImpl<I, O> contextClient;
        private final ClientResponder<I, O> clientResponder;

        private ServerContextPair(final ProtocolClientInitiatorImpl<I, O> contextClient, final ClientResponder<I, O> clientResponder) {
            if (contextClient == null) {
                throw new NullPointerException("clientInitiator is null");
            }
            if (clientResponder == null) {
                throw new NullPointerException("clientResponder is null");
            }
            this.contextClient = contextClient;
            this.clientResponder = clientResponder;
        }
    }

    private static final class ClientServicePair<I, O> {
        private final ServiceInitiator serviceInitiator;
        private final ProtocolServiceResponderImpl<I, O> serviceServer;

        private ClientServicePair(final ServiceInitiator serviceInitiator, final ProtocolServiceResponderImpl<I, O> serviceServer) {
            if (serviceInitiator == null) {
                throw new NullPointerException("serviceInitiator is null");
            }
            if (serviceServer == null) {
                throw new NullPointerException("serviceResponder is null");
            }
            this.serviceInitiator = serviceInitiator;
            this.serviceServer = serviceServer;
        }
    }

    private static final class ServerServicePair<I, O> {
        private final ProtocolServiceInitiatorImpl serviceClient;
        private final ServiceResponder<I, O> serviceResponder;

        private ServerServicePair(final ProtocolServiceInitiatorImpl serviceClient, final ServiceResponder<I, O> serviceResponder) {
            if (serviceClient == null) {
                throw new NullPointerException("serviceInitiator is null");
            }
            if (serviceResponder == null) {
                throw new NullPointerException("serviceResponder is null");
            }
            this.serviceClient = serviceClient;
            this.serviceResponder = serviceResponder;
        }
    }

    private final class ProtocolServiceInitiatorImpl implements ServiceInitiator {
        private final ServiceIdentifier serviceIdentifier;

        public ProtocolServiceInitiatorImpl(final ServiceIdentifier serviceIdentifier) {
            if (serviceIdentifier == null) {
                throw new NullPointerException("serviceIdentifier is null");
            }
            this.serviceIdentifier = serviceIdentifier;
        }

        public void handleClosing() throws RemotingException {
            try {
                protocolHandler.sendServiceClosing(serviceIdentifier);
            } catch (RemotingException e) {
                throw e;
            } catch (IOException e) {
                throw new RemotingException("Failed to send service closing message: " + e.getMessage(), e);
            }
        }
    }

    private final class ProtocolServiceResponderImpl<I, O> implements ServiceResponder<I, O> {
        private final ServiceIdentifier serviceIdentifier;

        public ProtocolServiceResponderImpl(final ServiceIdentifier serviceIdentifier) {
            this.serviceIdentifier = serviceIdentifier;
        }

        public void handleClose() throws RemotingException {
            try {
                protocolHandler.sendServiceClose(serviceIdentifier);
            } catch (RemotingException e) {
                throw e;
            } catch (IOException e) {
                throw new RemotingException("Failed to send service close message: " + e.getMessage(), e);
            }
        }

        public ClientResponder<I, O> createNewClient(final ClientInitiator clientInitiator) throws RemotingException {
            try {
                final ClientIdentifier clientIdentifier = protocolHandler.openClient(serviceIdentifier);
                if (clientIdentifier == null) {
                    throw new NullPointerException("clientIdentifier is null");
                }
                clientContexts.put(clientIdentifier, new ClientContextPair<I, O>(clientInitiator, new ProtocolClientResponderImpl<I, O>(clientIdentifier), clientIdentifier));
                return new ProtocolClientResponderImpl<I, O>(clientIdentifier);
            } catch (RemotingException e) {
                throw e;
            } catch (IOException e) {
                throw new RemotingException("Failed to open a context: " + e.getMessage(), e);
            }
        }
    }

    private final class ProtocolClientInitiatorImpl<I, O> implements ClientInitiator {
        private final ClientIdentifier clientIdentifier;
        private final ConcurrentMap<RequestIdentifier, RequestResponder<I>> requests = CollectionUtil.concurrentMap();

        public ProtocolClientInitiatorImpl(final ClientIdentifier clientIdentifier) {
            this.clientIdentifier = clientIdentifier;
        }

        public void handleClosing(boolean done) throws RemotingException {
            try {
                if (state.inHold(State.UP)) {
                    protocolHandler.sendClientClosing(clientIdentifier, done);
                }
            } catch (RemotingException e) {
                throw e;
            } catch (IOException e) {
                throw new RemotingException("Failed to send context closing message: " + e.getMessage(), e);
            }
        }

        private RequestInitiator<O> addClient(RequestIdentifier identifier) {
            return new ProtocolRequestInitiatorImpl<O>(clientIdentifier, identifier);
        }

        private final class ProtocolRequestInitiatorImpl<O> implements RequestInitiator<O> {
            private final ClientIdentifier clientIdentifer;
            private final RequestIdentifier requestIdentifer;

            public ProtocolRequestInitiatorImpl(final ClientIdentifier clientIdentifer, final RequestIdentifier requestIdentifer) {
                this.clientIdentifer = clientIdentifer;
                this.requestIdentifer = requestIdentifer;
            }

            public void handleReply(final O reply) throws RemotingException {
                try {
                    protocolHandler.sendReply(clientIdentifer, requestIdentifer, reply);
                } catch (RemotingException e) {
                    throw e;
                } catch (IOException e) {
                    throw new RemotingException("Failed to send a reply: " + e.getMessage(), e);
                } finally {
                   requests.remove(requestIdentifer);
                }
            }

            public void handleException(final RemoteExecutionException cause) throws RemotingException {
                try {
                    protocolHandler.sendException(clientIdentifer, requestIdentifer, cause);
                } catch (RemotingException e) {
                    throw e;
                } catch (IOException e) {
                    throw new RemotingException("Failed to send an exception: " + e.getMessage(), e);
                } finally {
                   requests.remove(requestIdentifer);
                }
            }

            public void handleCancelAcknowledge() throws RemotingException {
                try {
                    protocolHandler.sendCancelAcknowledge(clientIdentifer, requestIdentifer);
                } catch (RemotingException e) {
                    throw e;
                } catch (IOException e) {
                    throw new RemotingException("Failed to send a cancel acknowledgement: " + e.getMessage(), e);
                } finally {
                   requests.remove(requestIdentifer);
                }
            }
        }
    }

    private final class ProtocolClientResponderImpl<I, O> implements ClientResponder<I, O> {
        private final ClientIdentifier clientIdentifier;
        private final ConcurrentMap<RequestIdentifier, RequestInitiator<O>> requests = CollectionUtil.concurrentMap();

        public ProtocolClientResponderImpl(final ClientIdentifier clientIdentifier) {
            this.clientIdentifier = clientIdentifier;
        }

        public RequestResponder<I> createNewRequest(final RequestInitiator<O> requestInitiator) throws RemotingException {
            try {
                final RequestIdentifier requestIdentifier = protocolHandler.openRequest(clientIdentifier);
                if (requestIdentifier == null) {
                    throw new NullPointerException("requestIdentifier is null");
                }
                requests.put(requestIdentifier, requestInitiator);
                return new ProtocolRequestResponderImpl(requestIdentifier);
            } catch (RemotingException e) {
                throw e;
            } catch (IOException e) {
                throw new RemotingException("Failed to open a request: " + e.getMessage(), e);
            }
        }

        public void handleClose(final boolean immediate, final boolean cancel) throws RemotingException {
            try {
                protocolHandler.sendClientClose(clientIdentifier, immediate, cancel, false);
            } catch (RemotingException e) {
                throw e;
            } catch (IOException e) {
                throw new RemotingException("Failed to send context close message: " + e.getMessage(), e);
            }
        }

        private final class ProtocolRequestResponderImpl implements RequestResponder<I> {
            private final RequestIdentifier requestIdentifier;

            public ProtocolRequestResponderImpl(final RequestIdentifier requestIdentifier) {
                this.requestIdentifier = requestIdentifier;
            }

            public void handleRequest(final I request, final Executor streamExecutor) throws RemotingException {
                try {
                    protocolHandler.sendRequest(clientIdentifier, requestIdentifier, request, streamExecutor);
                } catch (RemotingException e) {
                    throw e;
                } catch (IOException e) {
                    throw new RemotingException("Failed to send a request: " + e.getMessage(), e);
                }
            }

            public void handleCancelRequest(final boolean mayInterrupt) throws RemotingException {
                try {
                    protocolHandler.sendCancelRequest(clientIdentifier, requestIdentifier, mayInterrupt);
                } catch (RemotingException e) {
                    throw e;
                } catch (IOException e) {
                    throw new RemotingException("Failed to send a cancel request: " + e.getMessage(), e);
                }
            }
        }
    }
}
