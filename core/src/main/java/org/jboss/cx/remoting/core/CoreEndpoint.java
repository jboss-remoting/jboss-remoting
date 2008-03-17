package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
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
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;
import org.jboss.cx.remoting.spi.Registration;

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
    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.INITIAL);

    private OrderedExecutorFactory orderedExecutorFactory;
    private Executor executor;

    static {
        Logger.getLogger("org.jboss.cx.remoting").info("JBoss Remoting version %s", Version.VERSION);
    }

    private enum State {
        INITIAL,
        UP,
        DOWN,
    }

    public CoreEndpoint(final String name) {
        this.name = name;
    }

    private final ConcurrentMap<Object, Object> endpointMap = CollectionUtil.concurrentMap();
    private final ConcurrentMap<String, CoreProtocolRegistration> protocolMap = CollectionUtil.concurrentMap();
    private final Set<CoreSession> sessions = CollectionUtil.synchronizedSet(CollectionUtil.<CoreSession>hashSet());
    // accesses protected by {@code shutdownListeners} - always lock AFTER {@code state}
    private final List<CloseHandler<Endpoint>> closeHandlers = CollectionUtil.synchronizedArrayList();

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
        orderedExecutorFactory = new OrderedExecutorFactory(executor);
    }

    public Endpoint getUserEndpoint() {
        return userEndpoint;
    }

    void removeSession(CoreSession coreSession) {
        sessions.remove(coreSession);
        sessions.notifyAll();
    }

    public void start() {
        state.requireTransition(State.INITIAL, State.UP);
    }

    public void stop() {
        // todo
    }

    Executor getOrderedExecutor() {
        return orderedExecutorFactory.getOrderedExecutor();
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

    public final class CoreProtocolRegistration implements Registration {
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

        public <I, O> ProtocolContext openIncomingSession(final ProtocolHandler handler, final Context<I, O> rootContext) throws RemotingException {
            return null;
        }

        public String getName() {
            return name;
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
            if (state.transitionHold(State.UP, State.DOWN)) try {
                Iterator<CloseHandler<Endpoint>> it = closeHandlers.iterator();
                while (it.hasNext()) {
                    CloseHandler<Endpoint> handler = it.next();
                    handler.handleClose(this);
                    it.remove();
                }
            } finally {
                state.release();
            }
        }

        public void closeImmediate() throws RemotingException {
            if (state.transitionHold(State.UP, State.DOWN)) try {
                Iterator<CloseHandler<Endpoint>> it = closeHandlers.iterator();
                while (it.hasNext()) {
                    CloseHandler<Endpoint> handler = it.next();
                    handler.handleClose(this);
                    it.remove();
                }
            } finally {
                state.release();
            }
        }

        public void addCloseHandler(final CloseHandler<Endpoint> closeHandler) {
            if (closeHandler == null) {
                throw new NullPointerException("closeHandler is null");
            }
            final State current = state.getStateHold();
            try {
                if (current != State.DOWN) {
                    closeHandlers.add(closeHandler);
                    return;
                }
            } finally {
                state.release();
            }
            closeHandler.handleClose(this);
        }
    }
}
