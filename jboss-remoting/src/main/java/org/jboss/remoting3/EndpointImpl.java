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

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.Handle;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.RequestHandlerSource;
import org.jboss.remoting3.spi.Cancellable;
import org.jboss.remoting3.spi.EndpointConnection;
import org.jboss.remoting3.spi.EndpointConnectionAcceptor;
import org.jboss.remoting3.spi.AbstractEndpointConnectionAcceptor;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.WeakCloseable;
import org.jboss.xnio.AbstractIoFuture;
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

    private static final Logger log = Logger.getLogger("org.jboss.remoting.endpoint");

    private final String name;

    /**
     * Snapshot lock.  Hold this lock while reading or updating {@link #serviceListenerRegistrations} or while updating
     * {@link #registeredLocalServices}.  Allows atomic snapshot of existing service registrations and listener add.
     */
    private final Lock serviceRegistrationLock = new ReentrantLock();

    /**
     * 
     */
    private final Queue<Registration<ServiceRegistrationListener>> serviceListenerRegistrations = concurrentLinkedQueue();

    private final ConcurrentMap<String, ServiceRegistration> registeredLocalServices = concurrentHashMap();

    private final ConcurrentMap<String, ConnectionProvider<?>> connectionProviders = concurrentHashMap();

    private final ConcurrentMap<Object, Object> endpointMap = concurrentHashMap();

    private static final EndpointPermission CREATE_ENDPOINT_PERM = new EndpointPermission("createEndpoint");
    private static final EndpointPermission CREATE_REQUEST_HANDLER_PERM = new EndpointPermission("createRequestHandler");
    private static final EndpointPermission REGISTER_SERVICE_PERM = new EndpointPermission("registerService");
    private static final EndpointPermission CREATE_CLIENT_PERM = new EndpointPermission("createClient");
    private static final EndpointPermission CREATE_CLIENT_SOURCE_PERM = new EndpointPermission("createClientSource");
    private static final EndpointPermission ADD_SERVICE_LISTENER_PERM = new EndpointPermission("addServiceListener");
    private static final EndpointPermission ADD_CONNECTION_PROVIDER_PERM = new EndpointPermission("addConnectionProvider");

    public EndpointImpl(final Executor executor, final String name) {
        super(executor);
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_ENDPOINT_PERM);
        }
        connectionProviders.put("jrs", new JrsConnectionProvider());
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

    public ConcurrentMap<Object, Object> getAttributes() {
        return endpointMap;
    }

    public <I, O> Handle<RequestHandler> createLocalRequestHandler(final RequestListener<I, O> requestListener, final Class<I> requestClass, final Class<O> replyClass) throws IOException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_REQUEST_HANDLER_PERM);
        }
        LocalRequestHandler.Config<I, O> config = new LocalRequestHandler.Config<I, O>(requestClass, replyClass);
        config.setExecutor(executor);
        config.setRequestListener(requestListener);
        config.setClientContext(new ClientContextImpl(executor));
        final LocalRequestHandler<I, O> localRequestHandler = new LocalRequestHandler<I, O>(config);
        final WeakCloseable lrhCloseable = new WeakCloseable(localRequestHandler);
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
        localRequestHandler.open();
        return localRequestHandler.getHandle();
    }

    public <I, O> Handle<RequestHandlerSource> registerService(final LocalServiceConfiguration<I, O> configuration) throws IOException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(REGISTER_SERVICE_PERM);
        }
        final String serviceType = configuration.getServiceType();
        final String groupName = configuration.getGroupName();
        final int metric = configuration.getMetric();
        if (metric < 0) {
            throw new IllegalArgumentException("metric must be greater than or equal to zero");
        }
        ServiceURI.validateServiceType(serviceType);
        ServiceURI.validateGroupName(groupName);
        final String serviceKey = serviceType.toLowerCase() + ":" + groupName.toLowerCase();
        final LocalRequestHandlerSource.Config<I, O> config = new LocalRequestHandlerSource.Config<I,O>(configuration.getRequestClass(), configuration.getReplyClass());
        config.setRequestListener(configuration.getRequestListener());
        config.setExecutor(executor);
        final LocalRequestHandlerSource<I, O> localRequestHandlerSource = new LocalRequestHandlerSource<I, O>(config);
        final ServiceRegistration registration = new ServiceRegistration(serviceType, groupName, name, localRequestHandlerSource);
        final AbstractSimpleCloseable newHandle = new AbstractSimpleCloseable(executor) {
            protected void closeAction() throws IOException {
                // todo fix
                registeredLocalServices.remove(serviceKey);
            }
        };
        registration.setHandle(newHandle);
        final Lock lock = serviceRegistrationLock;
        lock.lock();
        try {
            if (registeredLocalServices.putIfAbsent(serviceKey, registration) != null) {
                throw new ServiceRegistrationException("Registration of service of type \"" + serviceType + "\" in group \"" + groupName + "\" duplicates an already-registered service's specification");
            }
        } finally {
            lock.unlock();
        }
        final WeakCloseable lrhCloseable = new WeakCloseable(localRequestHandlerSource);
        final Key key = addCloseHandler(new CloseHandler<Endpoint>() {
            public void handleClose(final Endpoint closed) {
                IoUtils.safeClose(lrhCloseable);
            }
        });
        localRequestHandlerSource.addCloseHandler(new CloseHandler<RequestHandlerSource>() {
            public void handleClose(final RequestHandlerSource closed) {
                key.remove();
            }
        });
        localRequestHandlerSource.open();
        for (Registration<ServiceRegistrationListener> slr : serviceListenerRegistrations) {
            final ServiceRegistrationListener registrationListener = slr.getResource();
            try {
                final ServiceRegistrationListener.ServiceInfo serviceInfo = new ServiceRegistrationListener.ServiceInfo();
                serviceInfo.setGroupName(groupName);
                serviceInfo.setServiceType(serviceType);
                serviceInfo.setMetric(metric);
                serviceInfo.setRegistrationHandle(newHandle);
                serviceInfo.setRequestHandlerSource(localRequestHandlerSource);
                registrationListener.serviceRegistered(slr, serviceInfo);
            } catch (VirtualMachineError vme) {
                // panic!
                throw vme;
            } catch (Throwable t) {
                logListenerError(t);
            }
        }
        return localRequestHandlerSource.getHandle();
    }

    private static void logListenerError(final Throwable t) {
        log.error(t, "Service listener threw an exception");
    }

    public <I, O> Client<I, O> createClient(final RequestHandler requestHandler, final Class<I> requestType, final Class<O> replyType) throws IOException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_CLIENT_PERM);
        }
        boolean ok = false;
        final Handle<RequestHandler> handle = requestHandler.getHandle();
        try {
            final ClientImpl<I, O> client = ClientImpl.create(handle, executor, requestType, replyType);
            final WeakCloseable lrhCloseable = new WeakCloseable(new WeakReference<Closeable>(client));
            final Key key = addCloseHandler(new CloseHandler<Endpoint>() {
                public void handleClose(final Endpoint closed) {
                    IoUtils.safeClose(lrhCloseable);
                }
            });
            client.addCloseHandler(new CloseHandler<Client>() {
                public void handleClose(final Client closed) {
                    key.remove();
                }
            });
            ok = true;
            return client;
        } finally {
            if (! ok) {
                IoUtils.safeClose(handle);
            }
        }
    }

    public <I, O> ClientSource<I, O> createClientSource(final RequestHandlerSource requestHandlerSource, final Class<I> requestClass, final Class<O> replyClass) throws IOException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_CLIENT_SOURCE_PERM);
        }
        boolean ok = false;
        final Handle<RequestHandlerSource> handle = requestHandlerSource.getHandle();
        try {
            final ClientSourceImpl<I, O> clientSource = ClientSourceImpl.create(handle, this, requestClass, replyClass);
            final WeakCloseable lrhCloseable = new WeakCloseable(new WeakReference<Closeable>(clientSource));
            final Key key = addCloseHandler(new CloseHandler<Endpoint>() {
                public void handleClose(final Endpoint closed) {
                    IoUtils.safeClose(lrhCloseable);
                }
            });
            clientSource.addCloseHandler(new CloseHandler<ClientSource>() {
                public void handleClose(final ClientSource closed) {
                    key.remove();
                }
            });
            ok = true;
            return clientSource;
        } finally {
            if (! ok) {
                IoUtils.safeClose(handle);
            }
        }
    }

    public <I, O> IoFuture<ClientSource<I, O>> openClientSource(final URI uri, final Class<I> requestClass, final Class<O> replyClass) throws IllegalArgumentException {
        final ConnectionProvider<RequestHandlerSource> cp = getConnectionProvider(uri);
        if (cp.getResourceType() != ResourceType.CLIENT_SOURCE) {
            throw new IllegalArgumentException("URI can not be used to open a client source");
        }
        final FutureResult<ClientSource<I, O>> futureClientSource = new FutureResult<ClientSource<I, O>>();
        cp.connect(uri, new ConnectionProvider.Result<RequestHandlerSource>() {
            public void setResult(final RequestHandlerSource result) {
                final ClientSource<I, O> clientSource;
                try {
                    clientSource = createClientSource(result, requestClass, replyClass);
                } catch (IOException e) {
                    IoUtils.safeClose(result);
                    futureClientSource.setException(e);
                    return;
                }
                futureClientSource.setResult(clientSource);
            }

            public void setException(final IOException exception) {
                futureClientSource.setException(exception);
            }

            public void setCancelled() {
                futureClientSource.finishCancel();
            }
        });
        return futureClientSource;
    }

    public <I, O> IoFuture<? extends Client<I, O>> openClient(final URI uri, final Class<I> requestClass, final Class<O> replyClass) throws IllegalArgumentException {
        final ConnectionProvider<RequestHandler> cp = getConnectionProvider(uri);
        if (cp.getResourceType() != ResourceType.CLIENT) {
            throw new IllegalArgumentException("URI can not be used to open a client");
        }
        final FutureResult<Client<I, O>> futureClient = new FutureResult<Client<I, O>>();
        cp.connect(uri, new ConnectionProvider.Result<RequestHandler>() {
            public void setResult(final RequestHandler result) {
                final Client<I, O> client;
                try {
                    client = createClient(result, requestClass, replyClass);
                } catch (IOException e) {
                    IoUtils.safeClose(result);
                    futureClient.setException(e);
                    return;
                }
                futureClient.setResult(client);
            }

            public void setException(final IOException exception) {
                futureClient.setException(exception);
            }

            public void setCancelled() {
                futureClient.finishCancel();
            }
        });
        return futureClient;
    }

    public IoFuture<? extends Closeable> openEndpointConnection(final URI endpointUri) throws IllegalArgumentException {
        final ConnectionProvider<EndpointConnection> cp = getConnectionProvider(endpointUri);
        if (cp.getResourceType() != ResourceType.CLIENT) {
            throw new IllegalArgumentException("URI can not be used to open an endpoint connection");
        }
        final FutureResult<SimpleCloseable> futureEndpointConn = new FutureResult<SimpleCloseable>();
        cp.connect(endpointUri, new ConnectionProvider.Result<EndpointConnection>() {
            public void setResult(final EndpointConnection result) {
                if (futureEndpointConn.setResult(new AbstractSimpleCloseable(executor) {
                    protected void closeAction() throws IOException {
                        result.close();
                    }
                })) {
                    // todo - add the endpoint connection to the endpoint registry; notify listeners; etc.
                }
            }

            public void setException(final IOException exception) {
                futureEndpointConn.setException(exception);
            }

            public void setCancelled() {
                futureEndpointConn.finishCancel();
            }
        });
        return futureEndpointConn;
    }

    public EndpointConnectionAcceptor addConnectionProvider(final String uriScheme, final ConnectionProvider<?> provider) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_CONNECTION_PROVIDER_PERM);
        }
        final String key = uriScheme.toLowerCase();
        if (connectionProviders.putIfAbsent(key, provider) != null) {
            throw new IllegalArgumentException("Provider already registered for scheme \"" + uriScheme + "\"");
        }
        return new AbstractEndpointConnectionAcceptor(executor) {
            protected void closeAction() throws IOException {
                connectionProviders.remove(key, provider);
            }

            public void accept(final EndpointConnection connection) {
                // todo - add the endpoint connection to the endpoint registry; notify listeners; etc.
            }
        };
    }

    public ResourceType getResourceType(final URI uri) {
        final String scheme = uri.getScheme().toLowerCase();
        final ConnectionProvider<?> provider = connectionProviders.get(scheme);
        return provider != null ? provider.getResourceType() : ResourceType.UNKNOWN;
    }

    @SuppressWarnings({ "unchecked" })
    private <T> ConnectionProvider<T> getConnectionProvider(final URI uri) {
        if (uri == null) {
            throw new NullPointerException("serviceUri is null");
        }
        final String scheme = uri.getScheme();
        // this cast is checked later, indirectly
        final ConnectionProvider<T> cp = (ConnectionProvider<T>) connectionProviders.get(scheme);
        if (cp == null) {
            throw new IllegalArgumentException("No connection providers available for URI scheme \"" + scheme + "\"");
        }
        if (! ServiceURI.isRemotingServiceUri(uri)) {
            throw new IllegalArgumentException("Not a valid remoting service URI");
        }
        return cp;
    }

    public SimpleCloseable addServiceRegistrationListener(final ServiceRegistrationListener listener, final Set<ListenerFlag> flags) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_SERVICE_LISTENER_PERM);
        }
        final Registration<ServiceRegistrationListener> registration = new Registration<ServiceRegistrationListener>(executor, listener, serviceListenerRegistrations);
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
            serviceInfo.setRequestHandlerSource(service.getHandlerSource());
            serviceInfo.setServiceType(service.getServiceType());
            listener.serviceRegistered(registration, serviceInfo);
            if (! registration.isOpen()) {
                break;
            }
        }
        return registration;
    }

    private static final class Registration<T> extends AbstractSimpleCloseable {
        private final T resource;
        private final Queue<Registration<T>> resourceQueue;

        private Registration(final Executor executor, final T resource, final Queue<Registration<T>> resourceQueue) {
            super(executor);
            this.resource = resource;
            this.resourceQueue = resourceQueue;
        }

        protected void closeAction() throws IOException {
            resourceQueue.remove(this);
        }

        protected boolean isOpen() {
            return super.isOpen();
        }

        T getResource() {
            return resource;
        }
    }

    public String toString() {
        return "endpoint \"" + name + "\" <" + Integer.toHexString(hashCode()) + ">";
    }

    final class JrsConnectionProvider implements ConnectionProvider<RequestHandlerSource> {

        public Cancellable connect(final URI uri, final Result<RequestHandlerSource> requestHandlerSourceResult) throws IllegalArgumentException {
            final ServiceSpecification spec = ServiceSpecification.fromUri(uri);
            for (ServiceRegistration sr : registeredLocalServices.values()) {
                if (sr.matches(spec)) {
                    requestHandlerSourceResult.setResult(sr.getHandlerSource());
                }
            }
            // todo - iterate through discovered services as well
            return Cancellable.NULL_CANCELLABLE;
        }

        public URI getConnectionUri(final URI uri) {
            return uri;
        }

        public ResourceType getResourceType() {
            return ResourceType.CLIENT_SOURCE;
        }
    }

    /**
     *
     */
    static final class FutureResult<T> extends AbstractIoFuture<T> {

        protected boolean setException(final IOException exception) {
            return super.setException(exception);
        }

        protected boolean setResult(final T result) {
            return super.setResult(result);
        }

        protected boolean finishCancel() {
            return super.finishCancel();
        }
    }
}
