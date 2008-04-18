package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.ClientSource;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.SessionListener;
import org.jboss.cx.remoting.core.util.OrderedExecutorFactory;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.spi.Registration;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.util.NamingThreadFactory;
import org.jboss.cx.remoting.version.Version;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

/**
 *
 */
public class CoreEndpoint implements Endpoint {

    static {
        // Print Remoting "greeting" message
        Logger.getLogger("org.jboss.cx.remoting").info("JBoss Remoting version %s", Version.VERSION);
    }

    private enum State implements org.jboss.cx.remoting.util.State<State> {
        INITIAL,
        UP,
        DOWN;

        public boolean isReachable(final State dest) {
            return compareTo(dest) < 0;
        }
    }

    private String name;
    private RequestListener<?, ?> rootListener;

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.INITIAL);
    private final Set<SessionListener> sessionListeners = CollectionUtil.synchronizedSet(new LinkedHashSet<SessionListener>());

    private OrderedExecutorFactory orderedExecutorFactory;
    private ExecutorService executorService;

    private final ConcurrentMap<Object, Object> endpointMap = CollectionUtil.concurrentMap();
    private final ConcurrentMap<String, CoreProtocolRegistration> protocolMap = CollectionUtil.concurrentMap();
    private final Set<CoreSession> sessions = CollectionUtil.synchronizedSet(CollectionUtil.<CoreSession>hashSet());

    public CoreEndpoint() {
    }

    // Dependencies

    private Executor executor;

    public Executor getExecutor() {
        return executor;
    }

    public Executor getOrderedExecutor() {
        return orderedExecutorFactory.getOrderedExecutor();
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
        orderedExecutorFactory = new OrderedExecutorFactory(executor);
    }

    // Configuration

    public void setName(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setRootListener(final RequestListener<?, ?> rootListener) {
        this.rootListener = rootListener;
    }

    public RequestListener<?, ?> getRootListener() {
        return rootListener;
    }

    // Lifecycle

    public void create() {
        // todo security check
        if (rootListener == null) {
            throw new NullPointerException("rootListener is null");
        }
    }

    public void start() {
        // todo security check
        if (executor == null) {
            executorService = Executors.newCachedThreadPool(new NamingThreadFactory(Executors.defaultThreadFactory(), "Remoting endpoint %s"));
            setExecutor(executorService);
        }
        state.requireTransition(State.INITIAL, State.UP);
    }

    public void stop() {
        // todo security check
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
        // todo
    }

    public void destroy() {
        rootListener = null;
        executor = null;
    }


    // Endpoint implementation

    public ConcurrentMap<Object, Object> getAttributes() {
        return endpointMap;
    }

    public Session openSession(final URI uri, final AttributeMap attributeMap) throws RemotingException {
        if (uri == null) {
            throw new NullPointerException("uri is null");
        }
        if (attributeMap == null) {
            throw new NullPointerException("attributeMap is null");
        }
        final String scheme = uri.getScheme();
        if (scheme == null) {
            throw new RemotingException("No scheme on remote endpoint URI");
        }
        state.requireHold(State.UP);
        try {
            final CoreProtocolRegistration registration = protocolMap.get(scheme);
            if (registration == null) {
                throw new RemotingException("No handler available for URI scheme \"" + scheme + "\"");
            }
            final ProtocolHandlerFactory factory = registration.getProtocolHandlerFactory();
            try {
                final CoreSession session = new CoreSession(CoreEndpoint.this);
                session.initializeClient(factory, uri, attributeMap, createClient(rootListener));
                sessions.add(session);
                final Session userSession = session.getUserSession();
                for (final SessionListener listener : sessionListeners) {
                    executor.execute(new Runnable() {
                        public void run() {
                            listener.handleSessionOpened(userSession);
                        }
                    });
                }
                return userSession;
            } catch (IOException e) {
                RemotingException rex = new RemotingException("Failed to create protocol handler: " + e.getMessage());
                rex.setStackTrace(e.getStackTrace());
                throw rex;
            }
        } finally {
            state.release();
        }
    }

    public ProtocolContext openIncomingSession(final ProtocolHandler handler) throws RemotingException {
        state.requireHold(State.UP);
        try {
            final CoreSession session = new CoreSession(CoreEndpoint.this);
            session.initializeServer(handler, createClient(rootListener));
            sessions.add(session);
            return session.getProtocolContext();
        } finally {
            state.release();
        }
    }

    public Registration registerProtocol(final String scheme, final ProtocolHandlerFactory protocolHandlerFactory) throws RemotingException, IllegalArgumentException {
        if (scheme == null) {
            throw new NullPointerException("scheme is null");
        }
        if (protocolHandlerFactory == null) {
            throw new NullPointerException("protocolHandlerFactory is null");
        }
        state.requireHold(State.UP);
        try {
            final CoreProtocolRegistration registration = new CoreProtocolRegistration(protocolHandlerFactory);
            protocolMap.put(scheme, registration);
            return registration;
        } finally {
            state.release();
        }
    }

    public <I, O> Client<I, O> createClient(RequestListener<I, O> requestListener) {
        final CoreInboundClient<I, O> inbound = new CoreInboundClient<I, O>(requestListener, executor);
        final CoreOutboundClient<I, O> outbound = new CoreOutboundClient<I, O>(executor);
        inbound.initialize(outbound.getContextClient());
        outbound.initialize(inbound.getClientResponder());
        return outbound.getUserContext();
    }

    public <I, O> ClientSource<I, O> createService(RequestListener<I, O> requestListener) {
        final CoreInboundService<I, O> inbound = new CoreInboundService<I, O>(requestListener, executor);
        final CoreOutboundService<I, O> outbound = new CoreOutboundService<I, O>(executor);
        inbound.initialize(outbound.getServiceClient());
        outbound.initialize(inbound.getServiceResponder());
        return outbound.getUserContextSource();
    }

    public void addSessionListener(final SessionListener sessionListener) {
        // TODO security check
        sessionListeners.add(sessionListener);
    }

    public void removeSessionListener(final SessionListener sessionListener) {
        // TODO security check
        sessionListeners.remove(sessionListener);
    }

    void removeSession(CoreSession coreSession) {
        synchronized (sessions) {
            if (!sessions.remove(coreSession)) {
                return;
            }
            sessions.notifyAll();
        }
    }

    public final class CoreProtocolRegistration implements Registration {
        private final ProtocolHandlerFactory protocolHandlerFactory;

        private CoreProtocolRegistration(final ProtocolHandlerFactory protocolHandlerFactory) {
            this.protocolHandlerFactory = protocolHandlerFactory;
        }

        public void start() {
        }

        public void stop() {
        }

        public void unregister() {
        }

        private ProtocolHandlerFactory getProtocolHandlerFactory() {
            return protocolHandlerFactory;
        }
    }

    public static final class SimpleClientCallbackHandler implements CallbackHandler {
        private final String userName;
        private final char[] password;

        public SimpleClientCallbackHandler(final String userName, final char[] password) {
            this.userName = userName;
            this.password = password;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    ((NameCallback) callback).setName(userName);
                } else if (callback instanceof PasswordCallback) {
                    ((PasswordCallback) callback).setPassword(password);
                } else {
                    throw new UnsupportedCallbackException(callback, "This handler only supports username/password callbacks");
                }
            }
        }
    }
}
