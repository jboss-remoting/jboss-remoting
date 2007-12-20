package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Iterator;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.EndpointLocator;
import org.jboss.cx.remoting.InterceptorDeploymentSpec;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.ServiceDeploymentSpec;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.EndpointShutdownListener;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.core.util.AtomicStateMachine;
import org.jboss.cx.remoting.spi.Discovery;
import org.jboss.cx.remoting.spi.Registration;
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
    private CallbackHandler remoteCallbackHandler;
    private CallbackHandler localCallbackHandler;
    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.UP);

    private enum State {
        UP,
        DOWN,
    }

    protected CoreEndpoint(final String name) {
        this.name = name;
    }

    private final ConcurrentMap<Object, Object> endpointMap = CollectionUtil.concurrentMap();
    private final ConcurrentMap<String, CoreProtocolRegistration> protocolMap = CollectionUtil.concurrentMap();
    private final ConcurrentMap<ServiceKey, CoreDeployedService<?, ?>> services = CollectionUtil.concurrentMap();
    private final Set<CoreSession> sessions = CollectionUtil.synchronizedSet(CollectionUtil.<CoreSession>hashSet());
    // accesses protected by {@code shutdownListeners} - always lock AFTER {@code state}
    private final List<EndpointShutdownListener> shutdownListeners = CollectionUtil.arrayList();

    ConcurrentMap<Object, Object> getAttributes() {
        return endpointMap;
    }

    Endpoint getUserEndpoint() {
        return userEndpoint;
    }

    void removeSession(CoreSession coreSession) {
        sessions.remove(coreSession);
        sessions.notifyAll();
    }

    @SuppressWarnings ({"unchecked"})
    <I, O> CoreDeployedService<I, O> locateDeployedService(ServiceLocator<I, O> locator) {
        state.requireHold(State.UP);
        try {
            final String name = locator.getServiceGroupName();
            final String type = locator.getServiceType();
            // first try the quick (exact) lookup
            if (name.indexOf('*') == -1) {
                final CoreDeployedService<I, O> service = (CoreDeployedService<I, O>) services.get(new ServiceKey(name, type));
                if (service != null) {
                    return service;
                } else {
                    return null;
                }
            }
            final Pattern pattern = createWildcardPattern(name);
            for (Map.Entry<ServiceKey,CoreDeployedService<?,?>> entry : services.entrySet()) {
                final CoreEndpoint.ServiceKey key = entry.getKey();
                final String entryName = key.getName();
                final String entryType = key.getType();
                if (entryType.equals(type) && pattern.matcher(entryName).matches()) {
                    return (CoreDeployedService<I, O>) entry.getValue();
                }
            }
            return null;
        } finally {
            state.release();
        }
    }

    private static final Pattern wildcardPattern = Pattern.compile("^([^*]+|\\*)+$");

    private static Pattern createWildcardPattern(final String string) {
        final Matcher matcher = wildcardPattern.matcher(string);
        final StringBuilder target = new StringBuilder(string.length() * 2);
        while (matcher.find()) {
            final String val = matcher.group(1);
            if ("*".equals(val)) {
                target.append(".*");
            } else {
                target.append(Pattern.quote(val));
            }
        }
        return Pattern.compile(target.toString());
    }

    private final class ServiceKey {
        private final String name;
        private final String type;

        private ServiceKey(final String name, final String type) {
            this.name = name;
            this.type = type;
        }

        private String getName() {
            return name;
        }

        private String getType() {
            return type;
        }

        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final ServiceKey that = (ServiceKey) o;

            if (name != null ? !name.equals(that.name) : that.name != null) return false;
            if (type != null ? !type.equals(that.type) : that.type != null) return false;

            return true;
        }

        public int hashCode() {
            int result;
            result = (name != null ? name.hashCode() : 0);
            result = 31 * result + (type != null ? type.hashCode() : 0);
            return result;
        }
    }

    public final class CoreProtocolServerContext implements ProtocolServerContext {
        private CoreProtocolServerContext() {
        }

        public ProtocolContext establishSession(ProtocolHandler handler) {
            final CoreSession session = new CoreSession(CoreEndpoint.this, handler);
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

        public void shutdown() {
            final List<EndpointShutdownListener> listeners;
            synchronized(state) {
                if (!state.transition(State.UP, State.DOWN)) {
                    return;
                }
                synchronized(shutdownListeners) {
                    listeners = CollectionUtil.arrayList(shutdownListeners);
                    shutdownListeners.clear();
                }
            }
            for (EndpointShutdownListener listener : listeners) {
                listener.handleShutdown(this);
            }
            for (CoreSession coreSession : sessions) {
                coreSession.shutdown();
            }
            sessions.clear();
        }

        public void addShutdownListener(EndpointShutdownListener listener) {
            final State currentState = state.getStateHold();
            try {
                switch (currentState) {
                    case UP:
                        synchronized(shutdownListeners) {
                            shutdownListeners.add(listener);
                            return;
                        }
                    default:
                        // must be shut down!
                        listener.handleShutdown(this);
                }
            } finally {
                state.release();
            }
        }

        public void removeShutdownListener(EndpointShutdownListener listener) {
            synchronized(shutdownListeners) {
                final Iterator<EndpointShutdownListener> i = shutdownListeners.iterator();
                while (i.hasNext()) {
                    if (i.next() == listener) {
                        i.remove();
                    }
                }
            }
        }

        public Session openSession(final EndpointLocator endpointLocator) throws RemotingException {
            final String scheme = endpointLocator.getEndpointUri().getScheme();
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
                    final CoreSession session = new CoreSession(CoreEndpoint.this, factory, endpointLocator);
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

        public <I, O> Registration deployService(final ServiceDeploymentSpec<I, O> spec) throws RemotingException {
            if (spec.getServiceName() == null) {
                throw new NullPointerException("spec.getServiceName() is null");
            }
            if (spec.getServiceType() == null) {
                throw new NullPointerException("spec.getServiceType() is null");
            }
            if (spec.getRequestListener() == null) {
                throw new NullPointerException("spec.getRequestListener() is null");
            }
            state.requireHold(State.UP);
            try {
                final CoreDeployedService<I, O> service = new CoreDeployedService<I, O>(spec.getServiceName(), spec.getServiceType(), spec.getRequestListener());
                if (services.putIfAbsent(new ServiceKey(spec.getServiceName(), spec.getServiceType()), service) != null) {
                    throw new RemotingException("A service with the same name is already deployed");
                }
                // todo - return a registration instance
                return null;
            } finally {
                state.release();
            }
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

        public Registration deployInterceptorType(final InterceptorDeploymentSpec spec) throws RemotingException {
            // todo - interceptors
            return null;
        }

        public Discovery discover(String endpointName, URI nextHop, int cost) throws RemotingException {
            // todo - implement
            return null;
        }

        public CallbackHandler getRemoteCallbackHandler() {
            synchronized(this) {
                return remoteCallbackHandler;
            }
        }

        public CallbackHandler getLocalCallbackHandler() {
            synchronized(this) {
                return localCallbackHandler;
            }
        }

        public void setRemoteCallbackHandler(final CallbackHandler remoteCallbackHandler) {
            synchronized(this) {
                CoreEndpoint.this.remoteCallbackHandler = remoteCallbackHandler;
            }
        }

        public void setLocalCallbackHandler(final CallbackHandler localCallbackHandler) {
            synchronized(this) {
                CoreEndpoint.this.localCallbackHandler = localCallbackHandler;
            }
        }
    }
}
