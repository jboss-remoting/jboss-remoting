package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.ContextSource;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.core.util.OrderedExecutorFactory;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.version.Version;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

/**
 *
 */
public final class CoreEndpoint {

    private final String name;
    private final Endpoint userEndpoint = new UserEndpoint();
    private final OrderedExecutorFactory orderedExecutorFactory;
    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.UP);
    private final Executor executor;
    private final RequestListener<?, ?> rootRequestListener;

    static {
        Logger.getLogger("org.jboss.cx.remoting").info("JBoss Remoting version %s", Version.VERSION);
    }

    private enum State {
        UP,
        DOWN,
    }

    protected CoreEndpoint(final String name, final RequestListener<?, ?> rootRequestListener) {
        this.name = name;
        // todo - make this configurable
        executor = Executors.newCachedThreadPool();
        orderedExecutorFactory = new OrderedExecutorFactory(executor);
        this.rootRequestListener = rootRequestListener;
    }

    private final ConcurrentMap<Object, Object> endpointMap = CollectionUtil.concurrentMap();
    private final ConcurrentMap<String, CoreProtocolRegistration> protocolMap = CollectionUtil.concurrentMap();
    private final Set<CoreSession> sessions = CollectionUtil.synchronizedSet(CollectionUtil.<CoreSession>hashSet());
    // accesses protected by {@code shutdownListeners} - always lock AFTER {@code state}
    private final List<CloseHandler<Endpoint>> closeHandlers = CollectionUtil.arrayList();

    ConcurrentMap<Object, Object> getAttributes() {
        return endpointMap;
    }

    Executor getExecutor() {
        return executor;
    }

    Executor getOrderedExecutor() {
        return orderedExecutorFactory.getOrderedExecutor();
    }

    Endpoint getUserEndpoint() {
        return userEndpoint;
    }

    void removeSession(CoreSession coreSession) {
        sessions.remove(coreSession);
        sessions.notifyAll();
    }

    public final class CoreProtocolServerContext implements ProtocolServerContext {
        private CoreProtocolServerContext() {
        }

        public <I, O> ProtocolContext establishSession(final ProtocolHandler handler, final Context<I, O> rootContext) {
            final CoreSession session = new CoreSession(CoreEndpoint.this);
            session.initializeServer(handler, rootContext);
            return session.getProtocolContext();
        }
    }

    public final class CoreProtocolRegistration implements ProtocolRegistration {
        private final CoreProtocolServerContext protocolServerContext = new CoreProtocolServerContext();
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

        public ProtocolHandlerFactory getProtocolHandlerFactory() {
            return protocolHandlerFactory;
        }

        public ProtocolServerContext getProtocolServerContext() {
            return protocolServerContext;
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

    public final class UserEndpoint implements Endpoint {

        public ConcurrentMap<Object, Object> getAttributes() {
            return endpointMap;
        }

        public <I, O> Session openSession(final URI uri, final AttributeMap attributeMap, final Context<I, O> rootContext) throws RemotingException {
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
                    session.initializeClient(factory, uri, attributeMap, rootContext);
                    sessions.add(session);
                    return session.getUserSession();
                } catch (IOException e) {
                    RemotingException rex = new RemotingException("Failed to create protocol handler: " + e.getMessage());
                    rex.setStackTrace(e.getStackTrace());
                    throw rex;
                }
            } finally {
                state.release();
            }
        }

        public String getName() {
            return name;
        }

        public ProtocolRegistration registerProtocol(ProtocolRegistrationSpec spec) throws RemotingException, IllegalArgumentException {
            if (spec.getScheme() == null) {
                throw new NullPointerException("spec.getScheme() is null");
            }
            if (spec.getProtocolHandlerFactory() == null) {
                throw new NullPointerException("spec.getProtocolHandlerFactory() is null");
            }
            state.requireHold(State.UP);
            try {
                final CoreProtocolRegistration registration = new CoreProtocolRegistration(spec.getProtocolHandlerFactory());
                protocolMap.put(spec.getScheme(), registration);
                return registration;
            } finally {
                state.release();
            }
        }

        public <I, O> Context<I, O> createContext(RequestListener<I, O> requestListener) {
            final CoreInboundContext<I, O> inbound = new CoreInboundContext<I, O>(requestListener, executor);
            final CoreOutboundContext<I, O> outbound = new CoreOutboundContext<I, O>(executor);
            inbound.initialize(outbound.getContextClient());
            outbound.initialize(inbound.getContextServer());
            return outbound.getUserContext();
        }

        public <I, O> ContextSource<I, O> createService(RequestListener<I, O> requestListener) {
            final CoreInboundService<I, O> inbound = new CoreInboundService<I, O>(requestListener, executor);
            final CoreOutboundService<I, O> outbound = new CoreOutboundService<I, O>(executor);
            inbound.initialize(outbound.getServiceClient());
            outbound.initialize(inbound.getServiceServer());
            return outbound.getUserContextSource();
        }

        public void close() throws RemotingException {
            // todo ...
        }

        public void closeImmediate() throws RemotingException {
            // todo ...
        }

        public void addCloseHandler(final CloseHandler<Endpoint> closeHandler) {
            // todo ...
        }
    }
}
