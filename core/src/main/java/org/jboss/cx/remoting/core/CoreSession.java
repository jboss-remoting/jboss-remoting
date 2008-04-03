package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.core.stream.DefaultStreamDetector;
import org.jboss.cx.remoting.core.util.DelegatingObjectInput;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.spi.ByteMessageInput;
import org.jboss.cx.remoting.spi.ByteMessageOutput;
import org.jboss.cx.remoting.spi.ObjectMessageInput;
import org.jboss.cx.remoting.spi.ObjectMessageOutput;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.spi.stream.StreamDetector;
import org.jboss.cx.remoting.spi.stream.StreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.serial.io.JBossObjectInputStream;
import org.jboss.serial.io.JBossObjectOutputStream;


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

    // Contexts and services that are available on the remote end of this session
    // In these paris, the Server points to the ProtocolHandler, and the Client points to...whatever
    private final ConcurrentMap<ContextIdentifier, ClientContextPair> clientContexts = CollectionUtil.concurrentMap();
    private final ConcurrentMap<ServiceIdentifier, ClientServicePair> clientServices = CollectionUtil.concurrentMap();

    // Contexts and services that are available on this end of this session
    // In these pairs, the Client points to the ProtocolHandler, and the Server points to... whatever
    private final ConcurrentMap<ContextIdentifier, ServerContextPair> serverContexts = CollectionUtil.concurrentMap();
    private final ConcurrentMap<ServiceIdentifier, ServerServicePair> serverServices = CollectionUtil.concurrentMap();

    // streams - strong references, only clean up if a close message is sent or received
    private final ConcurrentMap<StreamIdentifier, CoreStream> streams = CollectionUtil.concurrentMap();

    // don't GC the endpoint while a session lives
    private final CoreEndpoint endpoint;
    private final Executor executor;

    /** The protocol handler.  Set on NEW -> CONNECTING */
    private ProtocolHandler protocolHandler;
    /** The remote endpoint name.  Set on CONNECTING -> UP */
    private String remoteEndpointName;
    /** The root context.  Set on CONNECTING -> UP */
    private Context<?, ?> rootContext;

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.NEW);

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

    // Initializers

    private <I, O> void doInitialize(final ProtocolHandler protocolHandler, final Context<I, O> rootContext) {
        if (protocolHandler == null) {
            throw new NullPointerException("protocolHandler is null");
        }
        this.protocolHandler = protocolHandler;
        if (rootContext instanceof AbstractRealContext) {
            final AbstractRealContext<I, O> abstractRealContext = (AbstractRealContext<I, O>) rootContext;
            // Forward local context
            final ContextIdentifier localIdentifier = protocolHandler.getLocalRootContextIdentifier();
            if (localIdentifier == null) {
                throw new NullPointerException("localIdentifier is null");
            }
            final ProtocolContextClientImpl<I, O> contextClient = new ProtocolContextClientImpl<I, O>(localIdentifier);
            serverContexts.put(localIdentifier, new ServerContextPair<I, O>(contextClient, abstractRealContext.getContextServer()));
            log.trace("Initialized session with local context %s", localIdentifier);
        }
        // Forward remote context
        final ContextIdentifier remoteIdentifier = protocolHandler.getRemoteRootContextIdentifier();
        if (remoteIdentifier == null) {
            throw new NullPointerException("remoteIdentifier is null");
        }
        final ProtocolContextServerImpl<I, O> contextServer = new ProtocolContextServerImpl<I,O>(remoteIdentifier);
        final CoreOutboundContext<I, O> coreOutboundContext = new CoreOutboundContext<I, O>(executor);
        clientContexts.put(remoteIdentifier, new ClientContextPair<I, O>(coreOutboundContext.getContextClient(), contextServer, remoteIdentifier));
        coreOutboundContext.initialize(contextServer);
        this.rootContext = coreOutboundContext.getUserContext();
        log.trace("Initialized session with remote context %s", remoteIdentifier);
    }

    <I, O> void initializeServer(final ProtocolHandler protocolHandler, final Context<I, O> rootContext) {
        if (protocolHandler == null) {
            throw new NullPointerException("protocolHandler is null");
        }
        boolean ok = false;
        state.requireTransitionExclusive(State.NEW, State.CONNECTING);
        try {
            doInitialize(protocolHandler, rootContext);
            ok = true;
        } finally {
            state.releaseExclusive();
            if (! ok) {
                state.transition(State.DOWN);
            }
        }
    }

    <I, O> void initializeClient(final ProtocolHandlerFactory protocolHandlerFactory, final URI remoteUri, final AttributeMap attributeMap, final Context<I, O> rootContext) throws IOException {
        if (protocolHandlerFactory == null) {
            throw new NullPointerException("protocolHandlerFactory is null");
        }
        boolean ok = false;
        state.requireTransitionExclusive(State.NEW, State.CONNECTING);
        try {
            doInitialize(protocolHandlerFactory.createHandler(protocolContext, remoteUri, attributeMap), rootContext);
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

    // Context mgmt

    // User session impl

    public final class UserSession implements Session {
        private UserSession() {}

        private final ConcurrentMap<Object, Object> sessionMap = CollectionUtil.concurrentMap();

        public void close() throws RemotingException {
            shutdown();
            // todo - should this be non-blocking?
            state.waitFor(State.DOWN);
        }

        public void closeImmediate() throws RemotingException {
            // todo ...
        }

        public void addCloseHandler(final CloseHandler<Session> closeHandler) {
            // todo ...
        }

        public ConcurrentMap<Object, Object> getAttributes() {
            return sessionMap;
        }

        public String getLocalEndpointName() {
            return endpoint.getUserEndpoint().getName();
        }

        public String getRemoteEndpointName() {
            return remoteEndpointName;
        }

        @SuppressWarnings ({"unchecked"})
        public <I, O> Context<I, O> getRootContext() {
            return (Context<I, O>) rootContext;
        }
    }

    // Protocol context

    @SuppressWarnings ({"unchecked"})
    private static <O> void doSendReply(RequestClient<O> requestClient, Object data) throws RemotingException {
        requestClient.handleReply((O)data);
    }

    // Lifecycle

    private void shutdown() {
        if (state.transition(State.UP, State.STOPPING)) {
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
            return new ObjectMessageOutputImpl(target, streamDetectors, endpoint.getOrderedExecutor());
        }

        public ObjectMessageOutput getMessageOutput(ByteMessageOutput target, Executor streamExecutor) throws IOException {
            if (target == null) {
                throw new NullPointerException("target is null");
            }
            if (streamExecutor == null) {
                throw new NullPointerException("streamExecutor is null");
            }
            return new ObjectMessageOutputImpl(target, streamDetectors, streamExecutor);
        }

        public ObjectMessageInput getMessageInput(ByteMessageInput source) throws IOException {
            if (source == null) {
                throw new NullPointerException("source is null");
            }
            return new ObjectMessageInputImpl(source);
        }

        public String getLocalEndpointName() {
            return endpoint.getUserEndpoint().getName();
        }

        public void receiveContextClose(ContextIdentifier remoteContextIdentifier, final boolean immediate, final boolean cancel, final boolean interrupt) {
            if (remoteContextIdentifier == null) {
                throw new NullPointerException("remoteContextIdentifier is null");
            }
            final ServerContextPair contextPair = serverContexts.remove(remoteContextIdentifier);
            // todo - do the whole close operation
            try {
                contextPair.contextServer.handleClose(immediate, cancel, interrupt);
            } catch (RemotingException e) {
                log.trace(e, "Failed to forward a context close");
            }
        }

        public void closeStream(StreamIdentifier streamIdentifier) {
            if (streamIdentifier == null) {
                throw new NullPointerException("streamIdentifier is null");
            }
            final CoreStream coreStream = streams.remove(streamIdentifier);
            // todo - shut down stream
        }

        public void receiveServiceClose(ServiceIdentifier serviceIdentifier) {
            if (serviceIdentifier == null) {
                throw new NullPointerException("serviceIdentifier is null");
            }
            final ServerServicePair servicePair = serverServices.remove(serviceIdentifier);
            try {
                servicePair.serviceServer.handleClose();
            } catch (RemotingException e) {
                log.trace(e, "Failed to forward a service close");
            }
        }

        @SuppressWarnings ({"unchecked"})
        public void receiveOpenedContext(ServiceIdentifier remoteServiceIdentifier, ContextIdentifier remoteContextIdentifier) {
            if (remoteServiceIdentifier == null) {
                throw new NullPointerException("remoteServiceIdentifier is null");
            }
            if (remoteContextIdentifier == null) {
                throw new NullPointerException("remoteContextIdentifier is null");
            }
            try {
                final ServerServicePair servicePair = serverServices.get(remoteServiceIdentifier);
                final ProtocolContextClientImpl contextClient = new ProtocolContextClientImpl(remoteContextIdentifier);
                final ContextServer contextServer = servicePair.serviceServer.createNewContext(contextClient);
                // todo - who puts it in the map?
                serverContexts.put(remoteContextIdentifier, new ServerContextPair(contextClient, contextServer));
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
                servicePair.serviceClient.handleClosing();
            } catch (RemotingException e) {
                log.trace(e, "Failed to signal that a service was closing on the remote side");
            }
        }

        public void receiveContextClosing(ContextIdentifier contextIdentifier, boolean done) {
            if (contextIdentifier == null) {
                throw new NullPointerException("contextIdentifier is null");
            }
            final ClientContextPair contextPair = clientContexts.get(contextIdentifier);
            try {
                contextPair.contextClient.handleClosing(done);
            } catch (RemotingException e) {
                log.trace(e, "Failed to signal that a context was closing on the remote side");
            }
        }

        public void receiveReply(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Object reply) {
            if (contextIdentifier == null) {
                throw new NullPointerException("contextIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final ClientContextPair contextPair = clientContexts.get(contextIdentifier);
            if (contextPair == null) {
                log.trace("Got reply for request %s on unknown context %s", requestIdentifier, contextIdentifier);
            } else {
                final ProtocolContextServerImpl<?, ?> contextServer = contextPair.contextServerRef.get();
                if (contextServer == null) {
                    log.trace("Got reply for request %s on unknown recently leaked context %s", requestIdentifier, contextIdentifier);
                } else {
                    final RequestClient<?> requestClient = (RequestClient<?>) contextServer.requests.get(requestIdentifier);
                    if (requestClient == null) {
                        log.trace("Got reply for unknown request %s on context %s", requestIdentifier, contextIdentifier);
                    } else try {
                        doSendReply(requestClient, reply);
                    } catch (RemotingException e) {
                        log.trace(e, "Failed to receive a reply");
                    }
                }
            }
        }

        public void receiveException(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) {
            if (contextIdentifier == null) {
                throw new NullPointerException("contextIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            if (exception == null) {
                throw new NullPointerException("exception is null");
            }
            final ClientContextPair contextPair = clientContexts.get(contextIdentifier);
            if (contextPair == null) {
                log.trace("Got exception reply for request %s on unknown context %s", requestIdentifier, contextIdentifier);
            } else {
                final ProtocolContextServerImpl<?, ?> contextServer = contextPair.contextServerRef.get();
                if (contextServer == null) {
                    log.trace("Got exception reply for request %s on unknown recently leaked context %s", requestIdentifier, contextIdentifier);
                } else {
                    final RequestClient<?> requestClient = (RequestClient<?>) contextServer.requests.get(requestIdentifier);
                    if (requestClient == null) {
                        log.trace("Got exception reply for unknown request %s on context %s", requestIdentifier, contextIdentifier);
                    } else try {
                        requestClient.handleException(exception);
                    } catch (RemotingException e) {
                        log.trace(e, "Failed to receive an exception reply");
                    }
                }
            }
        }

        public void receiveCancelAcknowledge(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier) {
            if (contextIdentifier == null) {
                throw new NullPointerException("contextIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final ClientContextPair contextPair = clientContexts.get(contextIdentifier);
            if (contextPair == null) {
                log.trace("Got cancellation acknowledgement for request %s on unknown context %s", requestIdentifier, contextIdentifier);
            } else {
                final ProtocolContextServerImpl<?, ?> contextServer = contextPair.contextServerRef.get();
                if (contextServer == null) {
                    log.trace("Got cancellation acknowledgement for request %s on unknown recently leaked context %s", requestIdentifier, contextIdentifier);
                } else {
                    final RequestClient<?> requestClient = (RequestClient<?>) contextServer.requests.get(requestIdentifier);
                    if (requestClient == null) {
                        log.trace("Got cancellation acknowledgement for unknown request %s on context %s", requestIdentifier, contextIdentifier);
                    } else try {
                        requestClient.handleCancelAcknowledge();
                    } catch (RemotingException e) {
                        log.trace(e, "Failed to receive a cancellation acknowledgement");
                    }
                }
            }
        }

        public void receiveCancelRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) {
            if (remoteContextIdentifier == null) {
                throw new NullPointerException("remoteContextIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final ServerContextPair contextPair = serverContexts.get(remoteContextIdentifier);
            final RequestServer<?> requestServer = (RequestServer<?>) contextPair.contextClient.requests.get(requestIdentifier);
            try {
                requestServer.handleCancelRequest(mayInterrupt);
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
        public void openSession(String remoteEndpointName) {
            state.waitFor(State.CONNECTING);
            state.requireTransitionExclusive(State.CONNECTING, State.UP);
            try {
                CoreSession.this.remoteEndpointName = remoteEndpointName;
            } finally {
                state.releaseExclusive();
            }
        }

        @SuppressWarnings ({"unchecked"})
        public void receiveRequest(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier, final Object request) {
            if (remoteContextIdentifier == null) {
                throw new NullPointerException("remoteContextIdentifier is null");
            }
            if (requestIdentifier == null) {
                throw new NullPointerException("requestIdentifier is null");
            }
            final ServerContextPair contextPair = serverContexts.get(remoteContextIdentifier);
            if (contextPair == null) {
                log.trace("Received a request on an unknown context %s", remoteContextIdentifier);
                return;
            }
            try {
                final RequestClient client = contextPair.contextClient.addClient(requestIdentifier);
                final RequestServer requestServer = contextPair.contextServer.createNewRequest(client);
                requestServer.handleRequest(request, executor);
            } catch (RemotingException e) {
                e.printStackTrace();
            }
        }
    }

    // message output

    private final class ObjectMessageOutputImpl extends JBossObjectOutputStream implements ObjectMessageOutput {
        private final ByteMessageOutput target;
        private final List<StreamDetector> streamDetectors;
        private final List<StreamSerializer> streamSerializers = new ArrayList<StreamSerializer>();
        private final Executor streamExecutor;

        private ObjectMessageOutputImpl(final ByteMessageOutput target, final List<StreamDetector> streamDetectors, final Executor streamExecutor) throws IOException {
            super(new OutputStream() {
                public void write(int b) throws IOException {
                    target.write(b);
                }

                public void write(byte b[]) throws IOException {
                    target.write(b);
                }

                public void write(byte b[], int off, int len) throws IOException {
                    target.write(b, off, len);
                }

                public void flush() throws IOException {
                    target.flush();
                }

                public void close() throws IOException {
                    target.close();
                }
            }, true);
            if (target == null) {
                throw new NullPointerException("target is null");
            }
            if (streamDetectors == null) {
                throw new NullPointerException("streamDetectors is null");
            }
            if (streamExecutor == null) {
                throw new NullPointerException("streamExecutor is null");
            }
            enableReplaceObject(true);
            this.target = target;
            this.streamDetectors = streamDetectors;
            this.streamExecutor = streamExecutor;
        }

        public void commit() throws IOException {
            close();
            target.commit();
            for (StreamSerializer serializer : streamSerializers) {
                try {
                    serializer.handleOpen();
                } catch (Exception ex) {
                    // todo - log
                }
            }
            streamSerializers.clear();
        }

        public int getBytesWritten() throws IOException {
            flush();
            return target.getBytesWritten();
        }

        private final <I, O> ContextMarker doContextReplace(ContextServer<I, O> contextServer) throws IOException {
            final ContextIdentifier contextIdentifier = protocolHandler.openContext();
            final ProtocolContextClientImpl<I, O> contextClient = new ProtocolContextClientImpl<I, O>(contextIdentifier);
            new ServerContextPair<I, O>(contextClient, contextServer);
            return new ContextMarker(contextIdentifier);
        }

        private final <I, O> ContextSourceMarker doContextSourceReplace(ServiceServer<I, O> serviceServer) throws IOException {
            final ServiceIdentifier serviceIdentifier = protocolHandler.openService();
            final ProtocolServiceClientImpl serviceClient = new ProtocolServiceClientImpl(serviceIdentifier);
            new ServerServicePair<I, O>(serviceClient, serviceServer);
            return new ContextSourceMarker(serviceIdentifier);
        }

        protected Object replaceObject(Object obj) throws IOException {
            final Object testObject = super.replaceObject(obj);
            if (testObject instanceof AbstractRealContext) {
                return doContextReplace(((AbstractRealContext<?, ?>) obj).getContextServer());
            } else if (testObject instanceof AbstractRealContextSource) {
                return doContextSourceReplace(((AbstractRealContextSource<?, ?>) obj).getServiceServer());
            }
            for (StreamDetector detector : streamDetectors) {
                final StreamSerializerFactory factory = detector.detectStream(testObject);
                if (factory != null) {
                    final StreamIdentifier streamIdentifier = protocolHandler.openStream();
                    if (streamIdentifier == null) {
                        throw new NullPointerException("streamIdentifier is null");
                    }
                    final CoreStream stream = new CoreStream(CoreSession.this, streamExecutor, streamIdentifier, factory, testObject);
                    if (streams.putIfAbsent(streamIdentifier, stream) != null) {
                        throw new IOException("Duplicate stream identifier encountered: " + streamIdentifier);
                    }
                    streamSerializers.add(stream.getStreamSerializer());
                    log.trace("Writing stream marker for object: %s", testObject);
                    return new StreamMarker(factory.getClass(), streamIdentifier);
                }
            }
            return testObject;
        }
    }

    // message input

    private final class ObjectInputImpl extends JBossObjectInputStream {

        private ClassLoader classLoader;

        public ObjectInputImpl(final InputStream is) throws IOException {
            super(is);
            enableResolveObject(true);
        }

        public Object resolveObject(Object obj) throws IOException {
            final Object testObject = super.resolveObject(obj);
            if (testObject instanceof StreamMarker) {
                StreamMarker marker = (StreamMarker) testObject;
                final StreamIdentifier streamIdentifier = marker.getStreamIdentifier();
                if (streamIdentifier == null) {
                    throw new NullPointerException("streamIdentifier is null");
                }
                final StreamSerializerFactory streamSerializerFactory;
                try {
                    streamSerializerFactory = marker.getFactoryClass().newInstance();
                } catch (InstantiationException e) {
                    throw new IOException("Failed to instantiate a stream: " + e);
                } catch (IllegalAccessException e) {
                    throw new IOException("Failed to instantiate a stream: " + e);
                }
                final CoreStream stream = new CoreStream(CoreSession.this, endpoint.getOrderedExecutor(), streamIdentifier, streamSerializerFactory);
                if (streams.putIfAbsent(streamIdentifier, stream) != null) {
                    throw new IOException("Duplicate stream received");
                }
                return stream.getRemoteSerializer().getRemoteInstance();
            } else {
                return testObject;
            }
        }

        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            final String name = desc.getName();
            if (classLoader != null) {
                if (primitiveTypes.containsKey(name)) {
                    return primitiveTypes.get(name);
                } else {
                    return Class.forName(name, false, classLoader);
                }
            } else {
                return super.resolveClass(desc);
            }
        }

        protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
            return super.resolveProxyClass(interfaces);
        }

        public Object readObject(final ClassLoader loader) throws ClassNotFoundException, IOException {
            classLoader = loader;
            try {
                return readObject();
            } finally {
                classLoader = null;
            }
        }
    }

    private final class ObjectMessageInputImpl extends DelegatingObjectInput implements ObjectMessageInput {
        private CoreSession.ObjectInputImpl objectInput;

        private ObjectMessageInputImpl(final ObjectInputImpl objectInput) throws IOException {
            super(objectInput);
            this.objectInput = objectInput;
        }

        private ObjectMessageInputImpl(final ByteMessageInput source) throws IOException {
            this(new ObjectInputImpl(new InputStream() {
                public int read(byte b[]) throws IOException {
                    return source.read(b);
                }

                public int read(byte b[], int off, int len) throws IOException {
                    return source.read(b, off, len);
                }

                public int read() throws IOException {
                    return source.read();
                }

                public void close() throws IOException {
                    source.close();
                }

                public int available() throws IOException {
                    return source.remaining();
                }
            }));
        }

        public Object readObject() throws ClassNotFoundException, IOException {
            return objectInput.readObject();
        }

        public Object readObject(ClassLoader loader) throws ClassNotFoundException, IOException {
            return objectInput.readObject(loader);
        }

        public int remaining() {
            try {
                return objectInput.available();
            } catch (IOException e) {
                throw new IllegalStateException("Available failed", e);
            }
        }
    }

    private final class WeakProtocolContextServerReference<I, O> extends WeakReference<ProtocolContextServerImpl<I, O>> {
        private final ClientContextPair<I, O> contextPair;

        private WeakProtocolContextServerReference(ProtocolContextServerImpl<I, O> referent, ClientContextPair<I, O> contextPair) {
            super(referent);
            this.contextPair = contextPair;
        }

        public ProtocolContextServerImpl<I, O> get() {
            return super.get();
        }

        public boolean enqueue() {
            try {
                clientContexts.remove(contextPair.contextIdentifier, contextPair);
                // todo close?
            } finally {
                return super.enqueue();
            }
        }
    }

    private final class ClientContextPair<I, O> {
        private final ContextClient contextClient;
        private final WeakProtocolContextServerReference<I, O> contextServerRef;
        private final ContextIdentifier contextIdentifier;

        private ClientContextPair(final ContextClient contextClient, final ProtocolContextServerImpl<I, O> contextServer, final ContextIdentifier contextIdentifier) {
            this.contextClient = contextClient;
            this.contextIdentifier = contextIdentifier;
            contextServerRef = new WeakProtocolContextServerReference<I, O>(contextServer, this);
            // todo - auto-cleanup
        }
    }

    private static final class ServerContextPair<I, O> {
        private final ProtocolContextClientImpl<I, O> contextClient;
        private final ContextServer<I, O> contextServer;

        private ServerContextPair(final ProtocolContextClientImpl<I, O> contextClient, final ContextServer<I, O> contextServer) {
            if (contextClient == null) {
                throw new NullPointerException("contextClient is null");
            }
            if (contextServer == null) {
                throw new NullPointerException("contextServer is null");
            }
            this.contextClient = contextClient;
            this.contextServer = contextServer;
        }
    }

    private static final class ClientServicePair<I, O> {
        private final ServiceClient serviceClient;
        private final ProtocolServiceServerImpl<I, O> serviceServer;

        private ClientServicePair(final ServiceClient serviceClient, final ProtocolServiceServerImpl<I, O> serviceServer) {
            if (serviceClient == null) {
                throw new NullPointerException("serviceClient is null");
            }
            if (serviceServer == null) {
                throw new NullPointerException("serviceServer is null");
            }
            this.serviceClient = serviceClient;
            this.serviceServer = serviceServer;
        }
    }

    private static final class ServerServicePair<I, O> {
        private final ProtocolServiceClientImpl serviceClient;
        private final ServiceServer<I, O> serviceServer;

        private ServerServicePair(final ProtocolServiceClientImpl serviceClient, final ServiceServer<I, O> serviceServer) {
            if (serviceClient == null) {
                throw new NullPointerException("serviceClient is null");
            }
            if (serviceServer == null) {
                throw new NullPointerException("serviceServer is null");
            }
            this.serviceClient = serviceClient;
            this.serviceServer = serviceServer;
        }
    }

    private final class ProtocolServiceClientImpl implements ServiceClient {
        private final ServiceIdentifier serviceIdentifier;

        public ProtocolServiceClientImpl(final ServiceIdentifier serviceIdentifier) {
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

    private final class ProtocolServiceServerImpl<I, O> implements ServiceServer<I, O> {
        private final ServiceIdentifier serviceIdentifier;

        public ProtocolServiceServerImpl(final ServiceIdentifier serviceIdentifier) {
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

        public ContextServer<I, O> createNewContext(final ContextClient client) throws RemotingException {
            try {
                final ContextIdentifier contextIdentifier = protocolHandler.openContext(serviceIdentifier);
                if (contextIdentifier == null) {
                    throw new NullPointerException("contextIdentifier is null");
                }
                clientContexts.put(contextIdentifier, new ClientContextPair<I, O>(client, new ProtocolContextServerImpl<I, O>(contextIdentifier), contextIdentifier));
                return new ProtocolContextServerImpl<I, O>(contextIdentifier);
            } catch (RemotingException e) {
                throw e;
            } catch (IOException e) {
                throw new RemotingException("Failed to open a context: " + e.getMessage(), e);
            }
        }
    }

    private final class ProtocolContextClientImpl<I, O> implements ContextClient {
        private final ContextIdentifier contextIdentifier;
        private final ConcurrentMap<RequestIdentifier, RequestServer<I>> requests = CollectionUtil.concurrentMap();

        public ProtocolContextClientImpl(final ContextIdentifier contextIdentifier) {
            this.contextIdentifier = contextIdentifier;
        }

        public void handleClosing(boolean done) throws RemotingException {
            try {
                if (state.inHold(State.UP)) {
                    protocolHandler.sendContextClosing(contextIdentifier, done);
                }
            } catch (RemotingException e) {
                throw e;
            } catch (IOException e) {
                throw new RemotingException("Failed to send context closing message: " + e.getMessage(), e);
            }
        }

        private RequestClient<O> addClient(RequestIdentifier identifier) {
            return new ProtocolRequestClientImpl<O>(contextIdentifier, identifier);
        }

        private final class ProtocolRequestClientImpl<O> implements RequestClient<O> {
            private final ContextIdentifier contextIdentifer;
            private final RequestIdentifier requestIdentifer;

            public ProtocolRequestClientImpl(final ContextIdentifier contextIdentifer, final RequestIdentifier requestIdentifer) {
                this.contextIdentifer = contextIdentifer;
                this.requestIdentifer = requestIdentifer;
            }

            public void handleReply(final O reply) throws RemotingException {
                try {
                    protocolHandler.sendReply(contextIdentifer, requestIdentifer, reply);
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
                    protocolHandler.sendException(contextIdentifer, requestIdentifer, cause);
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
                    protocolHandler.sendCancelAcknowledge(contextIdentifer, requestIdentifer);
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

    private final class ProtocolContextServerImpl<I, O> implements ContextServer<I, O> {
        private final ContextIdentifier contextIdentifier;
        private final ConcurrentMap<RequestIdentifier, RequestClient<O>> requests = CollectionUtil.concurrentMap();

        public ProtocolContextServerImpl(final ContextIdentifier contextIdentifier) {
            this.contextIdentifier = contextIdentifier;
        }

        public RequestServer<I> createNewRequest(final RequestClient<O> requestClient) throws RemotingException {
            try {
                final RequestIdentifier requestIdentifier = protocolHandler.openRequest(contextIdentifier);
                if (requestIdentifier == null) {
                    throw new NullPointerException("requestIdentifier is null");
                }
                requests.put(requestIdentifier, requestClient);
                return new ProtocolRequestServerImpl(requestIdentifier);
            } catch (RemotingException e) {
                throw e;
            } catch (IOException e) {
                throw new RemotingException("Failed to open a request: " + e.getMessage(), e);
            }
        }

        public void handleClose(final boolean immediate, final boolean cancel, final boolean interrupt) throws RemotingException {
            try {
                protocolHandler.sendContextClose(contextIdentifier, immediate, cancel, interrupt);
            } catch (RemotingException e) {
                throw e;
            } catch (IOException e) {
                throw new RemotingException("Failed to send context close message: " + e.getMessage(), e);
            }
        }

        private final class ProtocolRequestServerImpl implements RequestServer<I> {
            private final RequestIdentifier requestIdentifier;

            public ProtocolRequestServerImpl(final RequestIdentifier requestIdentifier) {
                this.requestIdentifier = requestIdentifier;
            }

            public void handleRequest(final I request, final Executor streamExecutor) throws RemotingException {
                try {
                    protocolHandler.sendRequest(contextIdentifier, requestIdentifier, request, streamExecutor);
                } catch (RemotingException e) {
                    throw e;
                } catch (IOException e) {
                    throw new RemotingException("Failed to send a request: " + e.getMessage(), e);
                }
            }

            public void handleCancelRequest(final boolean mayInterrupt) throws RemotingException {
                try {
                    protocolHandler.sendCancelRequest(contextIdentifier, requestIdentifier, mayInterrupt);
                } catch (RemotingException e) {
                    throw e;
                } catch (IOException e) {
                    throw new RemotingException("Failed to send a cancel request: " + e.getMessage(), e);
                }
            }
        }
    }

    private static final Map<String, Class<?>> primitiveTypes = new HashMap<String, Class<?>>();

    private static <T> void add(Class<T> type) {
        primitiveTypes.put(type.getName(), type);
    }

    static {
        add(void.class);
        add(boolean.class);
        add(byte.class);
        add(short.class);
        add(int.class);
        add(long.class);
        add(float.class);
        add(double.class);
        add(char.class);
    }
}
