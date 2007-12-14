package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.EndpointLocator;
import org.jboss.cx.remoting.InterceptorDeploymentSpec;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.ServiceDeploymentSpec;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.core.util.Resource;
import org.jboss.cx.remoting.spi.Discovery;
import org.jboss.cx.remoting.spi.Registration;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;

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
    private final Resource resource = new Resource();
    private final Endpoint userEndpoint = new UserEndpoint();
    private CallbackHandler remoteCallbackHandler;
    private CallbackHandler localCallbackHandler;

    protected CoreEndpoint(final String name) {
        this.name = name;
        resource.doStart(null);
    }

    private final ConcurrentMap<Object, Object> endpointMap = CollectionUtil.concurrentMap();
    private final ConcurrentMap<String, CoreProtocolRegistration> protocolMap = CollectionUtil.concurrentMap();
    private final ConcurrentMap<String, Service> services = CollectionUtil.concurrentMap();
    private final Set<CoreSession> sessions = CollectionUtil.synchronizedSet(CollectionUtil.<CoreSession>weakHashSet());

    public ConcurrentMap<Object, Object> getAttributes() {
        return endpointMap;
    }

    public Endpoint getUserEndpoint() {
        return userEndpoint;
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

    public final class Service {
        private final ServiceDeploymentSpec serviceDeploymentSpec;

        private Service(final ServiceDeploymentSpec serviceDeploymentSpec) {
            this.serviceDeploymentSpec = serviceDeploymentSpec;
        }

        private final Registration registration = new Registration() {
            private final Resource resource = new Resource();

            public void start() {
                resource.doStart(null);
            }

            public void stop() {
                resource.doStop(null, null);
            }

            public void unregister() {
                resource.doTerminate(new Runnable() {
                    public void run() {
                        services.remove(serviceDeploymentSpec.getServiceName(), Service.this);
                    }
                });
            }
        };


    }

    public final class UserEndpoint implements Endpoint {

        public ConcurrentMap<Object, Object> getAttributes() {
            return endpointMap;
        }

        public void shutdown() {
            resource.doStop(new Runnable() {
                public void run() {
                    // TODO - shut down sessions
                }
            }, null);
            resource.doTerminate(null);
        }

        public Session openSession(final EndpointLocator endpointLocator) throws RemotingException {
            boolean success = false;
            resource.doAcquire();
            try {
                final String scheme = endpointLocator.getEndpointUri().getScheme();
                if (scheme == null) {
                    throw new RemotingException("No scheme on remote endpoint URI");
                }
                final CoreProtocolRegistration registration = protocolMap.get(scheme);
                if (registration == null) {
                    throw new RemotingException("No handler available for URI scheme \"" + scheme + "\"");
                }
                final ProtocolHandlerFactory factory = registration.getProtocolHandlerFactory();
                try {
                    final CoreSession session = new CoreSession(CoreEndpoint.this, factory, endpointLocator);
                    sessions.add(session);
                    success = true;
                    return session.getUserSession();
                } catch (IOException e) {
                    RemotingException rex = new RemotingException("Failed to create protocol handler: " + e.getMessage());
                    rex.setStackTrace(e.getStackTrace());
                    throw rex;
                }
            } finally {
                if (!success) {
                    resource.doRelease();
                }
            }
        }

        public String getName() {
            return name;
        }

        public Registration deployService(final ServiceDeploymentSpec spec) throws RemotingException {
            if (spec.getServiceName() == null) {
                throw new IllegalArgumentException("Service name is not specified");
            }
            final Service service = new Service(spec);
            if (services.putIfAbsent(spec.getServiceName(), service) != null) {
                throw new RemotingException("A service with the same name is already deployed");
            }
            return service.registration;
        }

        public ProtocolRegistration registerProtocol(ProtocolRegistrationSpec spec) throws RemotingException, IllegalArgumentException {
            // todo validation, etc
            final CoreProtocolRegistration registration = new CoreProtocolRegistration(spec.getProtocolHandlerFactory());
            protocolMap.put(spec.getScheme(), registration);
            return registration;
        }

        public Registration deployInterceptorType(final InterceptorDeploymentSpec spec) throws RemotingException {
            return null;
        }

        public Discovery discover(String endpointName, URI nextHop, int cost) throws RemotingException {
            // todo - implement
            return null;
        }

        public CallbackHandler getRemoteCallbackHandler() {
            return remoteCallbackHandler;
        }

        public CallbackHandler getLocalCallbackHandler() {
            return localCallbackHandler;
        }

        public void setRemoteCallbackHandler(final CallbackHandler remoteCallbackHandler) {
            CoreEndpoint.this.remoteCallbackHandler = remoteCallbackHandler;
        }

        public void setLocalCallbackHandler(final CallbackHandler localCallbackHandler) {
            CoreEndpoint.this.localCallbackHandler = localCallbackHandler;
        }
    }
}
