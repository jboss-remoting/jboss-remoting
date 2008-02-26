package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.core.stream.DefaultStreamDetector;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.util.ByteInput;
import org.jboss.cx.remoting.util.ByteOutput;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.util.MessageInput;
import org.jboss.cx.remoting.util.MessageOutput;
import org.jboss.cx.remoting.log.Logger;
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
import org.jboss.cx.remoting.spi.wrapper.ContextWrapper;


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

    // clients - weak reference, to clean up if the user leaks
    private final ConcurrentMap<ContextIdentifier, WeakReference<CoreOutboundContext>> contexts = CollectionUtil.concurrentMap();
    private final ConcurrentMap<ServiceIdentifier, WeakReference<CoreOutboundService>> services = CollectionUtil.concurrentMap();

    // servers - strong refereces, only clean up if we hear it from the other end
    private final ConcurrentMap<ContextIdentifier, CoreInboundContext> serverContexts = CollectionUtil.concurrentMap();
    private final ConcurrentMap<ServiceIdentifier, CoreInboundService> serverServices = CollectionUtil.concurrentMap();

    // streams - strong references, only clean up if a close message is sent or received
    private final ConcurrentMap<StreamIdentifier, CoreStream> streams = CollectionUtil.concurrentMap();

    // don't GC the endpoint while a session lives
    private final CoreEndpoint endpoint;

    /** The protocol handler.  Set on NEW -> CONNECTING */
    private ProtocolHandler protocolHandler;
    /** The remote endpoint name.  Set on CONNECTING -> UP */
    private String remoteEndpointName;
    /** The root client context.  Set on CONNECTING -> UP */
    private Context<?, ?> rootContext;

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.NEW);

    // Constructors

    CoreSession(final CoreEndpoint endpoint) {
        if (endpoint == null) {
            throw new NullPointerException("endpoint is null");
        }
        this.endpoint = endpoint;
        // todo - make stream detectors pluggable
        streamDetectors = java.util.Collections.<StreamDetector>singletonList(new DefaultStreamDetector());
    }

    // Initializers

    @SuppressWarnings ({"unchecked"})
    void initializeServer(final ProtocolHandler protocolHandler) {
        if (protocolHandler == null) {
            throw new NullPointerException("protocolHandler is null");
        }
        state.requireTransitionExclusive(State.NEW, State.CONNECTING);
        try {
            this.protocolHandler = protocolHandler;
            final RequestListener<?, ?> listener = endpoint.getRootRequestListener();
            if (listener != null) {
                final ContextIdentifier contextIdentifier = protocolHandler.getRemoteRootContextIdentifier();
                serverContexts.put(contextIdentifier, new CoreInboundContext(contextIdentifier, this, listener));
            }
        } finally {
            state.releaseExclusive();
        }
    }

    @SuppressWarnings ({"unchecked"})
    void initializeClient(final ProtocolHandlerFactory protocolHandlerFactory, final URI remoteUri, final AttributeMap attributeMap) throws IOException {
        if (protocolHandlerFactory == null) {
            throw new NullPointerException("protocolHandlerFactory is null");
        }
        state.requireTransitionExclusive(State.NEW, State.CONNECTING);
        try {
            protocolHandler = protocolHandlerFactory.createHandler(protocolContext, remoteUri, attributeMap);
            final RequestListener<?, ?> listener = endpoint.getRootRequestListener();
            if (listener != null) {
                final ContextIdentifier contextIdentifier = protocolHandler.getRemoteRootContextIdentifier();
                serverContexts.put(contextIdentifier, new CoreInboundContext(contextIdentifier, this, listener));
            }
        } finally {
            state.releaseExclusive();
        }
    }

    // Outbound protocol messages

    void sendRequest(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final Object request, final Executor streamExecutor) throws RemotingException {
        try {
            protocolHandler.sendRequest(contextIdentifier, requestIdentifier, request, streamExecutor);
        } catch (IOException e) {
            throw new RemotingException("Failed to send the request: " + e);
        }
    }

    boolean sendCancelRequest(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final boolean mayInterrupt) {
        try {
            protocolHandler.sendCancelRequest(contextIdentifier, requestIdentifier, mayInterrupt);
        } catch (IOException e) {
            log.trace("Failed to send a cancel request: %s", e);
            return false;
        }
        return true;
    }

    void sendReply(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final Object reply) throws RemotingException {
        try {
            protocolHandler.sendReply(contextIdentifier, requestIdentifier, reply);
        } catch (IOException e) {
            throw new RemotingException("Failed to send the reply: " + e);
        }
    }

    void sendException(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) throws RemotingException {
        try {
            protocolHandler.sendException(contextIdentifier, requestIdentifier, exception);
        } catch (IOException e) {
            throw new RemotingException("Failed to send the exception: " + e);
        }
    }

    void sendCancelAcknowledge(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier) throws RemotingException {
        try {
            protocolHandler.sendCancelAcknowledge(contextIdentifier, requestIdentifier);
        } catch (IOException e) {
            throw new RemotingException("Failed to send cancel acknowledgement: " + e);
        }
    }

    // Inbound protocol messages are in the ProtocolContextImpl

    // Other protocol-related

    RequestIdentifier openRequest(final ContextIdentifier contextIdentifier) throws RemotingException {
        try {
            return protocolHandler.openRequest(contextIdentifier);
        } catch (IOException e) {
            throw new RemotingException("Failed to open a request: " + e);
        }
    }

    void closeService(final ServiceIdentifier serviceIdentifier) throws RemotingException {
        try {
            protocolHandler.closeService(serviceIdentifier);
        } catch (IOException e) {
            throw new RemotingException("Failed to close service: " + e);
        }
    }

    // Getters

    ProtocolContext getProtocolContext() {
        return protocolContext;
    }

    Session getUserSession() {
        return userSession;
    }

    CoreEndpoint getEndpoint() {
        return endpoint;
    }

    ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    Executor getExecutor() {
        return endpoint.getExecutor();
    }

    // Thread-local instance

    private static final ThreadLocal<CoreSession> instance = new ThreadLocal<CoreSession>();

    static CoreSession getInstance() {
        return instance.get();
    }

    private void setInstance() {
        instance.set(this);
    }

    private void clearInstance() {
        instance.remove();
    }

    // State mgmt

    private enum State {
        NEW,
        CONNECTING,
        UP,
        STOPPING,
        DOWN,
    }

    void shutdown() {
        if (state.transition(State.UP, State.STOPPING)) {
            for (Map.Entry<ContextIdentifier,WeakReference<CoreOutboundContext>> entry : contexts.entrySet()) {
                final CoreOutboundContext context = entry.getValue().get();
                if (context != null) {
                    context.receiveCloseContext();
                }
            }
            for (Map.Entry<ContextIdentifier,CoreInboundContext> entry : serverContexts.entrySet()) {
                entry.getValue().shutdown();
            }
            state.requireTransition(State.STOPPING, State.DOWN);
        }
    }

    // Context mgmt

    <I, O> CoreOutboundContext<I, O> createContext(final ServiceIdentifier serviceIdentifier) throws RemotingException {
        if (serviceIdentifier == null) {
            throw new NullPointerException("serviceIdentifier is null");
        }
        state.requireHold(State.UP);
        try {
            final ContextIdentifier contextIdentifier;
            try {
                contextIdentifier = protocolHandler.openContext(serviceIdentifier);
            } catch (IOException e) {
                RemotingException rex = new RemotingException("Failed to open context: " + e.getMessage());
                rex.setStackTrace(e.getStackTrace());
                throw rex;
            }
            final CoreOutboundContext<I, O> context = new CoreOutboundContext<I, O>(this, contextIdentifier);
            log.trace("Adding new context, ID = %s", contextIdentifier);
            contexts.put(contextIdentifier, new WeakReference<CoreOutboundContext>(context));
            return context;
        } finally {
            state.release();
        }
    }

    <I, O> CoreInboundContext<I, O> createServerContext(final ServiceIdentifier remoteServiceIdentifier, final ContextIdentifier remoteContextIdentifier, final RequestListener<I, O> requestListener) {
        if (remoteServiceIdentifier == null) {
            throw new NullPointerException("remoteServiceIdentifier is null");
        }
        if (remoteContextIdentifier == null) {
            throw new NullPointerException("remoteContextIdentifier is null");
        }
        state.requireHold(State.UP);
        try {
            final CoreInboundContext<I, O> context = new CoreInboundContext<I, O>(remoteContextIdentifier, this, requestListener);
            log.trace("Adding new server (inbound) context, ID = %s", remoteContextIdentifier);
            serverContexts.put(remoteContextIdentifier, context);
            return context;
        } finally {
            state.release();
        }
    }

    CoreOutboundContext getContext(final ContextIdentifier contextIdentifier) {
        if (contextIdentifier == null) {
            throw new NullPointerException("contextIdentifier is null");
        }
        final WeakReference<CoreOutboundContext> weakReference = contexts.get(contextIdentifier);
        return weakReference == null ? null : weakReference.get();
    }

    CoreInboundContext getServerContext(final ContextIdentifier remoteContextIdentifier) {
        if (remoteContextIdentifier == null) {
            throw new NullPointerException("remoteContextIdentifier is null");
        }
        final CoreInboundContext context = serverContexts.get(remoteContextIdentifier);
        return context;
    }

    void removeContext(final ContextIdentifier identifier) {
        if (identifier == null) {
            throw new NullPointerException("identifier is null");
        }
        contexts.remove(identifier);
    }

    void removeServerContext(final ContextIdentifier identifier) {
        if (identifier == null) {
            throw new NullPointerException("identifier is null");
        }
        serverContexts.remove(identifier);
    }

    // Service mgmt

    CoreOutboundService getService(final ServiceIdentifier serviceIdentifier) {
        if (serviceIdentifier == null) {
            throw new NullPointerException("serviceIdentifier is null");
        }
        final WeakReference<CoreOutboundService> weakReference = services.get(serviceIdentifier);
        return weakReference == null ? null : weakReference.get();
    }

    CoreInboundService getServerService(final ServiceIdentifier serviceIdentifier) {
        if (serviceIdentifier == null) {
            throw new NullPointerException("serviceIdentifier is null");
        }
        return serverServices.get(serviceIdentifier);
    }

    void removeServerService(final ServiceIdentifier serviceIdentifier) {
        if (serviceIdentifier == null) {
            throw new NullPointerException("serviceIdentifier is null");
        }
        serverServices.remove(serviceIdentifier);
    }

    // Stream mgmt

    void removeStream(final StreamIdentifier streamIdentifier) {
        streams.remove(streamIdentifier);
    }

    // User session impl

    public final class UserSession implements Session {
        private UserSession() {}

        private final ConcurrentMap<Object, Object> sessionMap = CollectionUtil.concurrentMap();

        public void close() throws RemotingException {
            shutdown();
            try {
                protocolHandler.closeSession();
            } catch (IOException e) {
                throw new RemotingException("Unable to close session: " + e.toString());
            }
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

    public final class ProtocolContextImpl implements ProtocolContext {

        public void closeSession() {
            shutdown();
        }

        public MessageOutput getMessageOutput(ByteOutput target) throws IOException {
            return new MessageOutputImpl(target, streamDetectors, endpoint.getOrderedExecutor());
        }

        public MessageOutput getMessageOutput(ByteOutput target, Executor streamExecutor) throws IOException {
            return new MessageOutputImpl(target, streamDetectors, streamExecutor);
        }

        public MessageInput getMessageInput(ByteInput source) throws IOException {
            return new MessageInputImpl(source);
        }

        public String getLocalEndpointName() {
            return endpoint.getUserEndpoint().getName();
        }

        public void closeContext(ContextIdentifier remoteContextIdentifier) {
            final CoreInboundContext context = getServerContext(remoteContextIdentifier);
            if (context != null) {
                context.shutdown();
            }
        }

        public void closeStream(StreamIdentifier streamIdentifier) {
            streams.remove(streamIdentifier);
        }

        public void closeService(ServiceIdentifier serviceIdentifier) {
            // todo
        }

        public void receiveOpenedContext(ServiceIdentifier remoteServiceIdentifier, ContextIdentifier remoteContextIdentifier) {
            final CoreInboundService service = getServerService(remoteServiceIdentifier);
            if (service != null) {
                service.receivedOpenedContext(remoteContextIdentifier);
            }
        }

        public void receiveServiceTerminate(ServiceIdentifier serviceIdentifier) {
            final CoreOutboundService service = getService(serviceIdentifier);
            if (service != null) {
                service.receiveServiceTerminate();
            } else {
                log.trace("Got service terminate for an unknown service (%s)", serviceIdentifier);
            }
        }

        @SuppressWarnings ({"unchecked"})
        public void receiveReply(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Object reply) {
            final CoreOutboundContext context = getContext(contextIdentifier);
            if (context != null) {
                context.receiveReply(requestIdentifier, reply);
            } else {
                log.trace("Got a reply for an unknown context (%s)", contextIdentifier);
            }
        }

        public void receiveException(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) {
            final CoreOutboundContext context = getContext(contextIdentifier);
            if (context != null) {
                context.receiveException(requestIdentifier, exception);
            } else {
                log.trace("Got a request exception for an unknown context (%s)", contextIdentifier);
            }
        }

        public void receiveCancelAcknowledge(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier) {
            final CoreOutboundContext context = getContext(contextIdentifier);
            if (context != null) {
                context.receiveCancelAcknowledge(requestIdentifier);
            } else {
                log.trace("Got a cancel acknowledge for an unknown context (%s)", contextIdentifier);
            }
        }

        public void receiveCancelRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) {
            final CoreInboundContext context = getServerContext(remoteContextIdentifier);
            context.receiveCancelRequest(requestIdentifier, mayInterrupt);
        }

        public void receiveStreamData(StreamIdentifier streamIdentifier, MessageInput data) {
            final CoreStream coreStream = streams.get(streamIdentifier);
            coreStream.receiveStreamData(data);
        }

        @SuppressWarnings ({"unchecked"})
        public void openSession(String remoteEndpointName) {
            state.waitForNotExclusive(State.NEW);
            try {
                state.requireTransition(State.CONNECTING, State.UP);
                CoreSession.this.remoteEndpointName = remoteEndpointName;
                final ContextIdentifier rootContextIdentifier = protocolHandler.getLocalRootContextIdentifier();
                final CoreOutboundContext outboundContext = new CoreOutboundContext(CoreSession.this, rootContextIdentifier);
                rootContext = new ContextWrapper(outboundContext.getUserContext()) {
                    public void close() throws RemotingException {
                        throw new RemotingException("close() not allowed on root context");
                    }
                };
                contexts.put(rootContextIdentifier, new WeakReference<CoreOutboundContext>(outboundContext));
            } finally {
                state.releaseExclusive();
            }
        }

        public void receiveRequest(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier, final Object request) {
            final CoreInboundContext context = getServerContext(remoteContextIdentifier);
            if (context != null) {
                endpoint.getExecutor().execute(new Runnable() {
                    @SuppressWarnings ({"unchecked"})
                    public void run() {
                        context.receiveRequest(requestIdentifier, request);
                    }
                });
            } else {
                log.trace("Got a request on an unknown context identifier (%s)", remoteContextIdentifier);
                try {
                    protocolHandler.sendException(remoteContextIdentifier, requestIdentifier, new RemoteExecutionException("Received a request on an invalid context"));
                } catch (IOException e) {
                    log.trace("Failed to send exception: %s", e.getMessage());
                }
            }
        }

    }

    // message output

    private final class MessageOutputImpl extends ObjectOutputStream implements MessageOutput {
        private final ByteOutput target;
        private final List<StreamDetector> streamDetectors;
        private final List<StreamSerializer> streamSerializers = new ArrayList<StreamSerializer>();
        private final Executor streamExecutor;

        private MessageOutputImpl(final ByteOutput target, final List<StreamDetector> streamDetectors, final Executor streamExecutor) throws IOException {
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
            });
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

        protected void writeObjectOverride(Object obj) throws IOException {
            setInstance();
            super.writeObjectOverride(obj);
            clearInstance();
        }

        protected Object replaceObject(Object obj) throws IOException {
            final Object testObject = super.replaceObject(obj);
            for (StreamDetector detector : streamDetectors) {
                final StreamSerializerFactory factory = detector.detectStream(testObject);
                if (factory != null) {
                    final StreamIdentifier streamIdentifier = protocolHandler.openStream();
                    final CoreStream stream = new CoreStream(CoreSession.this, streamExecutor, streamIdentifier, factory, testObject);
                    if (streams.putIfAbsent(streamIdentifier, stream) != null) {
                        throw new IOException("Duplicate stream identifier encountered: " + streamIdentifier);
                    }
                    streamSerializers.add(stream.getStreamSerializer());
                    return new StreamMarker(CoreSession.this, factory.getClass(), streamIdentifier);
                }
            }
            return testObject;
        }
    }

    // message input

    private final class ObjectInputImpl extends ObjectInputStream {

        private ClassLoader classLoader;

        public ObjectInputImpl(final InputStream is) throws IOException {
            super(is);
            super.enableResolveObject(true);
        }

        protected Object resolveObject(Object obj) throws IOException {
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

    private final class MessageInputImpl extends DelegatingObjectInput implements MessageInput {
        private CoreSession.ObjectInputImpl objectInput;

        private MessageInputImpl(final ObjectInputImpl objectInput) throws IOException {
            super(objectInput);
            this.objectInput = objectInput;
        }

        private MessageInputImpl(final ByteInput source) throws IOException {
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
            setInstance();
            try {
                return objectInput.readObject();
            } finally {
                clearInstance();
            }
        }

        public Object readObject(ClassLoader loader) throws ClassNotFoundException, IOException {
            setInstance();
            try {
                return objectInput.readObject(loader);
            } finally {
                clearInstance();
            }
        }

        public int remaining() {
            try {
                return objectInput.available();
            } catch (IOException e) {
                throw new IllegalStateException("Available failed", e);
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
