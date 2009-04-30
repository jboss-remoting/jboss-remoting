/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.AbstractSimpleCloseable;
import org.jboss.remoting3.spi.Cancellable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.RequestHandlerConnector;
import org.jboss.remoting3.spi.Result;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.WeakCloseable;
import org.jboss.xnio.log.Logger;

/**
 *
 */
final class EndpointImpl extends AbstractHandleableCloseable<Endpoint> implements Endpoint {

    static {
        // Print Remoting "greeting" message
        Logger.getLogger("org.jboss.remoting").info("JBoss Remoting version %s", Version.VERSION);
    }

    static <K, V> ConcurrentMap<K, V> concurrentHashMap() {
        return new ConcurrentHashMap<K, V>();
    }

    static <K, V> Map<K, V> hashMap() {
        return new HashMap<K, V>();
    }

    static <T> Set<T> hashSet() {
        return new HashSet<T>();
    }

    static <T> Queue<T> concurrentLinkedQueue() {
        return new ConcurrentLinkedQueue<T>();
    }

    static <T> List<T> arrayList() {
        return new ArrayList<T>();
    }

    private static final Logger log = Logger.getLogger("org.jboss.remoting.endpoint");

    private final String name;

    /**
     * Snapshot lock.  Hold this lock while reading or updating {@link #serviceListenerRegistrations} or while updating
     * {@link #registeredLocalServices}.  Allows atomic snapshot of existing service registrations and listener add.
     */
    private final Lock serviceRegistrationLock = new ReentrantLock();

    private final Set<Registration<ServiceRegistrationListener>> serviceListenerRegistrations = hashSet();
    private final Map<String, ServiceRegistration> registeredLocalServices = hashMap();

    private final ConcurrentMap<String, ConnectionProvider> connectionProviders = concurrentHashMap();

    private static final EndpointPermission CREATE_ENDPOINT_PERM = new EndpointPermission("createEndpoint");
    private static final EndpointPermission CREATE_REQUEST_HANDLER_PERM = new EndpointPermission("createRequestHandler");
    private static final EndpointPermission REGISTER_SERVICE_PERM = new EndpointPermission("registerService");
    private static final EndpointPermission CREATE_CLIENT_PERM = new EndpointPermission("createClient");
    private static final EndpointPermission ADD_SERVICE_LISTENER_PERM = new EndpointPermission("addServiceListener");
    private static final EndpointPermission CONNECT_PERM = new EndpointPermission("connect");
    private static final EndpointPermission ADD_CONNECTION_PROVIDER_PERM = new EndpointPermission("addConnectionProvider");

    public EndpointImpl(final Executor executor, final String name) {
        super(executor);
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_ENDPOINT_PERM);
        }
        this.executor = executor;
        this.name = name;
    }

    private final Executor executor;

    protected Executor getOrderedExecutor() {
        return new OrderedExecutor(executor);
    }

    protected Executor getExecutor() {
        return executor;
    }

    // Endpoint implementation

    public String getName() {
        return name;
    }

    public <I, O> RequestHandler createLocalRequestHandler(final RequestListener<I, O> requestListener, final Class<I> requestClass, final Class<O> replyClass) throws IOException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_REQUEST_HANDLER_PERM);
        }
        checkOpen();
        final ClientContextImpl clientContext = new ClientContextImpl(executor, loopbackConnection);
        final LocalRequestHandler<I, O> localRequestHandler = new LocalRequestHandler<I, O>(executor,
                requestListener, clientContext, requestClass, replyClass);
        final WeakCloseable lrhCloseable = new WeakCloseable(localRequestHandler);
        clientContext.addCloseHandler(new CloseHandler<ClientContext>() {
            public void handleClose(final ClientContext closed) {
                IoUtils.safeClose(localRequestHandler);
            }
        });
        final Key key = addCloseHandler(new CloseHandler<Endpoint>() {
            public void handleClose(final Endpoint closed) {
                IoUtils.safeClose(lrhCloseable);
            }
        });
        localRequestHandler.addCloseHandler(new CloseHandler<RequestHandler>() {
            public void handleClose(final RequestHandler closed) {
                key.remove();
            }
        });
        return localRequestHandler;
    }

    public <I, O> SimpleCloseable registerService(final LocalServiceConfiguration<I, O> configuration) throws IOException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(REGISTER_SERVICE_PERM);
        }
        if (configuration == null) {
            throw new NullPointerException("configuration is null");
        }
        final String serviceType = configuration.getServiceType();
        final String groupName = configuration.getGroupName();
        final int metric = configuration.getMetric();
        if (metric < 0) {
            throw new IllegalArgumentException("metric must be greater than or equal to zero");
        }
        ServiceURI.validateServiceType(serviceType);
        ServiceURI.validateGroupName(groupName);
        checkOpen();
        final String serviceKey = serviceType.toLowerCase() + ":" + groupName.toLowerCase();
        final Class<I> requestClass = configuration.getRequestClass();
        final Class<O> replyClass = configuration.getReplyClass();
        final ClientListener<I, O> clientListener = configuration.getClientListener();
        final Executor executor = this.executor;
        final Map<String, ServiceRegistration> registeredLocalServices = this.registeredLocalServices;
        final RequestHandlerConnector requestHandlerConnector = new RequestHandlerConnector() {
            public Cancellable createRequestHandler(final Result<RequestHandler> result) throws SecurityException {
                try {
                    final ClientContextImpl clientContext = new ClientContextImpl(executor, loopbackConnection);
                    final RequestListener<I, O> requestListener = clientListener.handleClientOpen(clientContext);
                    final RequestHandler localRequestHandler = createLocalRequestHandler(requestListener, requestClass, replyClass);
                    clientContext.addCloseHandler(new CloseHandler<ClientContext>() {
                        public void handleClose(final ClientContext closed) {
                            IoUtils.safeClose(localRequestHandler);
                        }
                    });
                    result.setResult(localRequestHandler);
                } catch (IOException e) {
                    result.setException(e);
                }
                return Cancellable.NULL_CANCELLABLE;
            }
        };
        final ServiceRegistration registration = new ServiceRegistration(serviceType, groupName, name, requestHandlerConnector);
        // this handle is used to remove the service registration
        final AbstractSimpleCloseable newHandle = new AbstractSimpleCloseable(executor) {
            protected void closeAction() throws IOException {
                final Lock lock = serviceRegistrationLock;
                lock.lock();
                try {
                    registeredLocalServices.remove(serviceKey);
                } finally {
                    lock.unlock();
                }
            }
        };
        registration.setHandle(newHandle);
        final List<Registration<ServiceRegistrationListener>> serviceListenerRegistrations;
        final Lock lock = serviceRegistrationLock;
        // actually register the service, and while we have the lock, snag a copy of the registration listener list
        lock.lock();
        try {
            if (registeredLocalServices.containsKey(serviceKey)) {
                throw new ServiceRegistrationException("Registration of service of type \"" + serviceType + "\" in group \"" + groupName + "\" duplicates an already-registered service's specification");
            }
            registeredLocalServices.put(serviceKey, registration);
            serviceListenerRegistrations = new ArrayList<Registration<ServiceRegistrationListener>>(this.serviceListenerRegistrations);
        } finally {
            lock.unlock();
        }
        // this registration closes the service registration when the endpoint is closed
        final WeakCloseable lrhCloseable = new WeakCloseable(newHandle);
        final Key key = addCloseHandler(new CloseHandler<Object>() {
            public void handleClose(final Object closed) {
                IoUtils.safeClose(lrhCloseable);
            }
        });
        // this registration removes the prior registration if the service registration is closed
        newHandle.addCloseHandler(new CloseHandler<Object>() {
            public void handleClose(final Object closed) {
                key.remove();
            }
        });
        // notify all service listener registrations that were registered at the time the service was created
        final ServiceRegistrationListener.ServiceInfo serviceInfo = new ServiceRegistrationListener.ServiceInfo();
        serviceInfo.setGroupName(groupName);
        serviceInfo.setServiceType(serviceType);
        serviceInfo.setMetric(metric);
        serviceInfo.setRegistrationHandle(newHandle);
        serviceInfo.setRequestHandlerConnector(requestHandlerConnector);
        executor.execute(new Runnable() {
            public void run() {
                for (final Registration<ServiceRegistrationListener> slr : serviceListenerRegistrations) {
                    final ServiceRegistrationListener registrationListener = slr.getResource();
                    try {
                        registrationListener.serviceRegistered(slr, serviceInfo.clone());
                    } catch (Throwable t) {
                        logListenerError(t);
                    }
                }
            }
        });
        return newHandle;
    }

    private static void logListenerError(final Throwable t) {
        log.error(t, "Service listener threw an exception");
    }

    public <I, O> Client<I, O> createClient(final RequestHandler requestHandler, final Class<I> requestType, final Class<O> replyType) throws IOException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_CLIENT_PERM);
        }
        if (requestHandler == null) {
            throw new NullPointerException("requestHandler is null");
        }
        if (requestType == null) {
            throw new NullPointerException("requestType is null");
        }
        if (replyType == null) {
            throw new NullPointerException("replyType is null");
        }
        checkOpen();
        final ClientImpl<I, O> client = ClientImpl.create(requestHandler, executor, requestType, replyType);
        final WeakCloseable lrhCloseable = new WeakCloseable(client);
        // this registration closes the client when the endpoint is closed
        final Key key = addCloseHandler(new CloseHandler<Endpoint>() {
            public void handleClose(final Endpoint closed) {
                IoUtils.safeClose(lrhCloseable);
            }
        });
        // this registration removes the prior registration if the client is closed
        client.addCloseHandler(new CloseHandler<Client>() {
            public void handleClose(final Client closed) {
                IoUtils.safeClose(requestHandler);
                key.remove();
            }
        });
        return client;
    }

    public SimpleCloseable addServiceRegistrationListener(final ServiceRegistrationListener listener, final Set<ListenerFlag> flags) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_SERVICE_LISTENER_PERM);
        }
        final Registration<ServiceRegistrationListener> registration = new Registration<ServiceRegistrationListener>(listener);
        final Lock lock = serviceRegistrationLock;
        final Collection<ServiceRegistration> services;
        lock.lock();
        try {
            if (flags == null || ! flags.contains(ListenerFlag.INCLUDE_OLD)) {
                services = new ArrayList<ServiceRegistration>(registeredLocalServices.values());
            } else {
                services = Collections.emptySet();
            }
        } finally {
            lock.unlock();
        }
        serviceListenerRegistrations.add(registration);
        for (ServiceRegistration service : services) {
            final ServiceRegistrationListener.ServiceInfo serviceInfo = new ServiceRegistrationListener.ServiceInfo();
            serviceInfo.setGroupName(service.getGroupName());
            serviceInfo.setMetric(service.getMetric());
            serviceInfo.setRegistrationHandle(service.getHandle());
            serviceInfo.setRequestHandlerConnector(service.getConnector());
            serviceInfo.setServiceType(service.getServiceType());
            listener.serviceRegistered(registration, serviceInfo);
            if (! registration.isOpen()) {
                break;
            }
        }
        return registration;
    }

    public IoFuture<? extends Connection> connect(final URI destination) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONNECT_PERM);
        }
        final ConnectionProvider connectionProvider = connectionProviders.get(destination.getScheme());
        final FutureResult<Connection, ConnectionHandlerFactory> futureResult = new FutureResult<Connection, ConnectionHandlerFactory>() {
            protected Connection translate(final ConnectionHandlerFactory result) {
                return new ConnectionImpl(result);
            }
        };
        connectionProvider.connect(destination, futureResult.getResult());
        return futureResult;
    }

    public void addConnectionProvider(final String uriScheme, final ConnectionProviderFactory providerFactory) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_CONNECTION_PROVIDER_PERM);
        }
        final ConnectionProviderContextImpl context = new ConnectionProviderContextImpl(executor, LOOPBACK_CONNECTION_HANDLER);
        final ConnectionProvider provider = providerFactory.createInstance(context);
        if (connectionProviders.putIfAbsent(uriScheme, provider) != null) {
            IoUtils.safeClose(context);
            throw new IllegalArgumentException("URI scheme '" + uriScheme + "' is already registered to a provider");
        }
        context.addCloseHandler(new CloseHandler<ConnectionProviderContext>() {
            public void handleClose(final ConnectionProviderContext closed) {
                connectionProviders.remove(uriScheme, provider);
            }
        });
    }

    public String toString() {
        return "endpoint \"" + name + "\" <" + Integer.toHexString(hashCode()) + ">";
    }

    private <I, O> IoFuture<? extends Client<I, O>> doOpenClient(final ConnectionHandler connectionHandler, final String serviceType, final String groupName, final Class<I> requestClass, final Class<O> replyClass) {
        final FutureResult<Client<I, O>, RequestHandler> futureResult = new FutureResult<Client<I, O>, RequestHandler>() {
            protected Client<I, O> translate(final RequestHandler result) throws IOException {
                return createClient(result, requestClass, replyClass);
            }
        };
        connectionHandler.open(serviceType, groupName, futureResult.getResult());
        return futureResult;
    }

    private class ConnectionImpl extends AbstractHandleableCloseable<Connection> implements Connection {
        private final ConnectionHandler connectionHandler;

        private ConnectionImpl(final ConnectionHandlerFactory connectionHandler) {
            super(EndpointImpl.this.executor);
            this.connectionHandler = connectionHandler.createInstance(LOOPBACK_CONNECTION_HANDLER);
        }

        public <I, O> IoFuture<? extends Client<I, O>> openClient(final String serviceType, final String groupName, final Class<I> requestClass, final Class<O> replyClass) {
            return doOpenClient(connectionHandler, serviceType, groupName, requestClass, replyClass);
        }

        public <I, O> ClientConnector<I, O> createClientConnector(final RequestListener<I, O> listener, final Class<I> requestClass, final Class<O> replyClass) throws IOException {
            final RequestHandler localRequestHandler = createLocalRequestHandler(listener, requestClass, replyClass);
            final RequestHandlerConnector connector = connectionHandler.createConnector(localRequestHandler);
            final ClientContextImpl context = new ClientContextImpl(executor, this);
            context.addCloseHandler(new CloseHandler<ClientContext>() {
                public void handleClose(final ClientContext closed) {
                    IoUtils.safeClose(localRequestHandler);
                }
            });
            return new ClientConnectorImpl<I, O>(connector, EndpointImpl.this, requestClass, replyClass, context);
        }
    }

    private static final class ConnectionProviderContextImpl extends AbstractHandleableCloseable<ConnectionProviderContext> implements ConnectionProviderContext {

        private final ConnectionHandler localConnectionHandler;

        private ConnectionProviderContextImpl(final Executor executor, final ConnectionHandler localConnectionHandler) {
            super(executor);
            this.localConnectionHandler = localConnectionHandler;
        }

        public void accept(final ConnectionHandlerFactory connectionHandlerFactory) {
            connectionHandlerFactory.createInstance(localConnectionHandler);
        }
    }

    private final class Registration<T> extends AbstractSimpleCloseable {
        private final T resource;

        private Registration(final T resource) {
            super(executor);
            this.resource = resource;
        }

        protected void closeAction() throws IOException {
            final Lock lock = serviceRegistrationLock;
            lock.lock();
            try {
                serviceListenerRegistrations.remove(this);
            } finally {
                lock.unlock();
            }
        }

        protected boolean isOpen() {
            return super.isOpen();
        }

        T getResource() {
            return resource;
        }
    }

    private final ConnectionHandler LOOPBACK_CONNECTION_HANDLER = new ConnectionHandler() {
        public Cancellable open(final String serviceName, final String groupName, final Result<RequestHandler> result) {
            // the loopback connection opens a local service
            // local services are registered as RequestHandlerConnectors
            final ServiceRegistration registration = registeredLocalServices.get(serviceName + ":" + groupName);
            if (registration != null) {
                return registration.getConnector().createRequestHandler(result);
            }
            result.setException(new ServiceNotFoundException(ServiceURI.create(serviceName, groupName, name), "No such service located"));
            return Cancellable.NULL_CANCELLABLE;
        }

        public RequestHandlerConnector createConnector(final RequestHandler localHandler) {
            // the loopback connection just returns the local handler directly as no forwarding is involved
            return new RequestHandlerConnector() {
                public Cancellable createRequestHandler(final Result<RequestHandler> result) throws SecurityException {
                    result.setResult(localHandler);
                    return Cancellable.NULL_CANCELLABLE;
                }
            };
        }

        public void close() {
            // not closeable
        }
    };

    private final Connection loopbackConnection = new Connection() {
        public <I, O> IoFuture<? extends Client<I, O>> openClient(final String serviceType, final String groupName, final Class<I> requestClass, final Class<O> replyClass) {
            return null;
        }

        public <I, O> ClientConnector<I, O> createClientConnector(final RequestListener<I, O> listener, final Class<I> requestClass, final Class<O> replyClass) {
            return null;
        }

        public void close() throws IOException {
        }

        public Key addCloseHandler(final CloseHandler<? super Connection> closeHandler) {
            return null;
        }
    };
}
