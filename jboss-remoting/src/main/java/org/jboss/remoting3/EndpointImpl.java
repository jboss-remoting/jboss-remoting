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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.RequestHandlerConnector;
import org.jboss.remoting3.spi.Result;
import org.jboss.remoting3.spi.ConnectionProviderRegistration;
import org.jboss.remoting3.spi.MarshallingProtocol;
import org.jboss.remoting3.spi.ConnectionContext;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.WeakCloseable;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.FinishedIoFuture;
import org.jboss.xnio.FailedIoFuture;
import org.jboss.xnio.log.Logger;
import org.jboss.marshalling.ClassTable;
import org.jboss.marshalling.ObjectTable;
import org.jboss.marshalling.ClassExternalizerFactory;
import org.jboss.marshalling.ClassResolver;
import org.jboss.marshalling.ObjectResolver;

/**
 *
 */
final class EndpointImpl extends AbstractHandleableCloseable<Endpoint> implements Endpoint {

    private static final NullCancellable NULL_CANCELLABLE = new NullCancellable();

    static {
        // Print Remoting "greeting" message
        Logger.getLogger("org.jboss.remoting").info("JBoss Remoting version %s", Version.VERSION);
    }

    static <K, V> ConcurrentMap<K, V> concurrentMap() {
        return new CopyOnWriteHashMap<K, V>();
    }

    static <K, V> ConcurrentMap<K, V> concurrentMap(Object lock) {
        return new CopyOnWriteHashMap<K, V>(lock);
    }

    static <K, V> ConcurrentMap<K, V> concurrentIdentityMap(Object lock) {
        return new CopyOnWriteHashMap<K, V>(true, lock);
    }

    static <T> Set<T> concurrentSet(Object lock) {
        return Collections.<T>newSetFromMap(EndpointImpl.<T, Boolean>concurrentMap(lock));
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
    private final Object serviceRegistrationLock = new Object();

    private final ConcurrentMap<Registration, ServiceRegistrationListener> serviceListenerRegistrations = concurrentIdentityMap(serviceRegistrationLock);
    private final ConcurrentMap<String, ConcurrentMap<String, ServiceRegistration>> registeredLocalServices = concurrentMap(serviceRegistrationLock);

    private final ConcurrentMap<String, ConnectionProvider<?>> connectionProviders = concurrentMap();
    private final ConcurrentMap<String, MarshallingProtocol> marshallingProtocols = concurrentMap();
    private final ConcurrentMap<String, ClassTable> classTables = concurrentMap();
    private final ConcurrentMap<String, ObjectTable> objectTables = concurrentMap();
    private final ConcurrentMap<String, ClassExternalizerFactory> classExternalizerFactories = concurrentMap();
    private final ConcurrentMap<String, ClassResolver> classResolvers = concurrentMap();
    private final ConcurrentMap<String, ObjectResolver> objectResolvers = concurrentMap();

    private static final EndpointPermission CREATE_ENDPOINT_PERM = new EndpointPermission("createEndpoint");
    private static final EndpointPermission CREATE_REQUEST_HANDLER_PERM = new EndpointPermission("createRequestHandler");
    private static final EndpointPermission REGISTER_SERVICE_PERM = new EndpointPermission("registerService");
    private static final EndpointPermission CREATE_CLIENT_PERM = new EndpointPermission("createClient");
    private static final EndpointPermission ADD_SERVICE_LISTENER_PERM = new EndpointPermission("addServiceListener");
    private static final EndpointPermission CONNECT_PERM = new EndpointPermission("connect");
    private static final EndpointPermission ADD_CONNECTION_PROVIDER_PERM = new EndpointPermission("addConnectionProvider");
    private static final EndpointPermission ADD_MARSHALLING_PROTOCOL_PERM = new EndpointPermission("addMarshallingProtocol");

    public EndpointImpl(final Executor executor, final String name) {
        super(executor);
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_ENDPOINT_PERM);
        }
        this.executor = executor;
        this.name = name;
        connectionProviders.put("local", new LocalConnectionProvider());
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

    public <I, O> Registration registerService(final LocalServiceConfiguration<I, O> configuration) throws IOException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(REGISTER_SERVICE_PERM);
        }
        if (configuration == null) {
            throw new NullPointerException("configuration is null");
        }
        final String serviceType = configuration.getServiceType();
        final String groupName = configuration.getGroupName();
        final OptionMap optionMap = configuration.getOptionMap();
        final Integer metric = optionMap.get(Options.METRIC);
        if (metric != null && metric.intValue() < 0) {
            throw new IllegalArgumentException("metric must be greater than or equal to zero");
        }
        ServiceURI.validateServiceType(serviceType);
        ServiceURI.validateGroupName(groupName);
        checkOpen();
        final String canonServiceType = serviceType.toLowerCase();
        final String canonGroupName = groupName.toLowerCase();
        final Class<I> requestClass = configuration.getRequestClass();
        final Class<O> replyClass = configuration.getReplyClass();
        final ClientListener<I, O> clientListener = configuration.getClientListener();
        final Executor executor = this.executor;
        final ConcurrentMap<String, ConcurrentMap<String, ServiceRegistration>> registeredLocalServices = this.registeredLocalServices;
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
                return NULL_CANCELLABLE;
            }
        };
        final ServiceRegistration registration = new ServiceRegistration(serviceType, groupName, name, optionMap, requestHandlerConnector);
        // this handle is used to remove the service registration
        final Registration handle = new Registration() {
            public void close() {
                synchronized (serviceRegistrationLock) {
                    final ConcurrentMap<String, ServiceRegistration> submap = registeredLocalServices.get(serviceType);
                    if (submap != null) {
                        submap.remove(groupName, registration);
                    }
                }
            }
        };
        registration.setHandle(handle);
        final Iterator<Map.Entry<Registration,ServiceRegistrationListener>> serviceListenerRegistrations;
        final Object lock = serviceRegistrationLock;
        synchronized (lock) {
            // actually register the service, and while we have the lock, snag a copy of the registration listener list
            final ConcurrentMap<String, ServiceRegistration> submap;
            if (registeredLocalServices.containsKey(canonServiceType)) {
                submap = registeredLocalServices.get(canonServiceType);
                if (submap.containsKey(canonGroupName)) {
                    throw new ServiceRegistrationException("ListenerRegistration of service of type \"" + serviceType + "\" in group \"" + groupName + "\" duplicates an already-registered service's specification");
                }
            } else {
                submap = concurrentMap(lock);
                registeredLocalServices.put(canonServiceType, submap);
            }
            submap.put(canonGroupName, registration);
            // snapshot
            serviceListenerRegistrations = this.serviceListenerRegistrations.entrySet().iterator();
        }
        // notify all service listener registrations that were registered at the time the service was created
        final ServiceRegistrationListener.ServiceInfo serviceInfo = new ServiceRegistrationListener.ServiceInfo();
        serviceInfo.setGroupName(groupName);
        serviceInfo.setServiceType(serviceType);
        serviceInfo.setOptionMap(optionMap);
        serviceInfo.setRegistrationHandle(handle);
        serviceInfo.setRequestHandlerConnector(requestHandlerConnector);
        executor.execute(new Runnable() {
            public void run() {
                final Iterator<Map.Entry<Registration,ServiceRegistrationListener>> iter = serviceListenerRegistrations;
                while (iter.hasNext()) {
                    final Map.Entry<Registration,ServiceRegistrationListener> slr = iter.next();
                    try {
                        slr.getValue().serviceRegistered(slr.getKey(), serviceInfo.clone());
                    } catch (Throwable t) {
                        logListenerError(t);
                    }
                }
            }
        });
        return handle;
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

    public Registration addServiceRegistrationListener(final ServiceRegistrationListener listener, final Set<ListenerFlag> flags) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_SERVICE_LISTENER_PERM);
        }
        final List<ServiceRegistration> services;
        final Registration registration = new Registration() {
            public void close() {
                serviceListenerRegistrations.remove(this);
            }
        };
        synchronized (serviceRegistrationLock) {
            serviceListenerRegistrations.put(registration, listener);
            if (flags == null || ! flags.contains(ListenerFlag.INCLUDE_OLD)) {
                // need to make a copy of the whole list
                services = new ArrayList<ServiceRegistration>();
                for (Map.Entry<String, ConcurrentMap<String, ServiceRegistration>> entry : registeredLocalServices.entrySet()) {
                    for (Map.Entry<String, ServiceRegistration> subEntry : entry.getValue().entrySet()) {
                        services.add(subEntry.getValue());
                    }
                }
            } else {
                services = null;
            }
        }
        if (services != null) {
            executor.execute(new Runnable() {
                public void run() {
                    for (ServiceRegistration service : services) {
                        final ServiceRegistrationListener.ServiceInfo serviceInfo = new ServiceRegistrationListener.ServiceInfo();
                        serviceInfo.setGroupName(service.getGroupName());
                        serviceInfo.setOptionMap(service.getOptionMap());
                        serviceInfo.setRegistrationHandle(service.getHandle());
                        serviceInfo.setRequestHandlerConnector(service.getRequestHandlerConnector());
                        serviceInfo.setServiceType(service.getServiceType());
                        try {
                            listener.serviceRegistered(registration, serviceInfo);
                        } catch (Throwable t) {
                            logListenerError(t);
                        }
                    }
                }
            });
        }
        return registration;
    }

    public IoFuture<? extends Connection> connect(final URI destination, final OptionMap connectOptions) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONNECT_PERM);
        }
        final String scheme = destination.getScheme();
        final ConnectionProvider<?> connectionProvider = connectionProviders.get(scheme);
        if (connectionProvider == null) {
            throw new UnknownURISchemeException("No connection provider for URI scheme \"" + scheme + "\" is installed");
        }
        final FutureResult<Connection, ConnectionHandlerFactory> futureResult = new FutureResult<Connection, ConnectionHandlerFactory>() {
            protected Connection translate(final ConnectionHandlerFactory result) {
                return new ConnectionImpl(result);
            }
        };
        connectionProvider.connect(destination, connectOptions, futureResult.getResult());
        return futureResult;
    }

    public <T> ConnectionProviderRegistration<T> addConnectionProvider(final String uriScheme, final ConnectionProviderFactory<T> providerFactory) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_CONNECTION_PROVIDER_PERM);
        }
        final ConnectionProviderContextImpl context = new ConnectionProviderContextImpl();
        final ConnectionProvider<T> provider = providerFactory.createInstance(context);
        if (connectionProviders.putIfAbsent(uriScheme, provider) != null) {
            IoUtils.safeClose(context);
            throw new DuplicateRegistrationException("URI scheme '" + uriScheme + "' is already registered to a provider");
        }
        context.addCloseHandler(new CloseHandler<ConnectionProviderContext>() {
            public void handleClose(final ConnectionProviderContext closed) {
                connectionProviders.remove(uriScheme, provider);
            }
        });
        final ConnectionProviderRegistration<T> handle = new ConnectionProviderRegistration<T>() {
            public void close() {
                IoUtils.safeClose(context);
            }

            public T getProviderInterface() {
                return provider.getProviderInterface();
            } 
        };
        context.addCloseHandler(new CloseHandler<ConnectionProviderContext>() {
            public void handleClose(final ConnectionProviderContext closed) {
                IoUtils.safeClose(handle);
            }
        });
        return handle;
    }

    private <T> Registration addMarshallingRegistration(final String name, final T target, final ConcurrentMap<String, T> map, final String descr) throws DuplicateRegistrationException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_MARSHALLING_PROTOCOL_PERM);
        }
        if ("default".equals(name)) {
            throw new IllegalArgumentException("'default' is not an allowed name");
        }
        if (map.putIfAbsent(name, target) != null) {
            throw new DuplicateRegistrationException(descr + " '" + name + "' is already registered");
        }
        return new MapRegistration<T>(map, name, target);
    }

    public Registration addMarshallingProtocol(final String name, final MarshallingProtocol marshallingProtocol) throws DuplicateRegistrationException {
        return addMarshallingRegistration(name, marshallingProtocol, marshallingProtocols, "Marshalling protocol");
    }

    public Registration addUserClassTable(final String name, final ClassTable classTable) throws DuplicateRegistrationException {
        return addMarshallingRegistration(name, classTable, classTables, "Class table");
    }

    public Registration addUserObjectTable(final String name, final ObjectTable objectTable) throws DuplicateRegistrationException {
        return addMarshallingRegistration(name, objectTable, objectTables, "Object table");
    }

    public Registration addUserExternalizerFactory(final String name, final ClassExternalizerFactory classExternalizerFactory) throws DuplicateRegistrationException {
        return addMarshallingRegistration(name, classExternalizerFactory, classExternalizerFactories, "Class externalizer factory");
    }

    public Registration addUserClassResolver(final String name, final ClassResolver classResolver) throws DuplicateRegistrationException {
        return addMarshallingRegistration(name, classResolver, classResolvers, "Class resolver");
    }

    public Registration addUserObjectResolver(final String name, final ObjectResolver objectResolver) throws DuplicateRegistrationException {
        return addMarshallingRegistration(name, objectResolver, objectResolvers, "Object resolver");
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

    private static class MapRegistration<T> implements Registration {
        private static final class Info<T> {
            private final ConcurrentMap<String, T> map;
            private final String key;
            private final T value;

            private Info(final ConcurrentMap<String, T> map, final String key, final T value) {
                this.map = map;
                this.key = key;
                this.value = value;
            }
        }
        private final AtomicReference<Info<T>> infoRef = new AtomicReference<Info<T>>();

        private MapRegistration(final ConcurrentMap<String, T> map, final String key, final T value) {
            infoRef.set(new Info<T>(map, key, value));
        }

        public void close() {
            final Info<T> info = infoRef.getAndSet(null);
            if (info != null) {
                info.map.remove(info.key, info.value);
            }
        }
    }

    private final class LocalConnectionContext implements ConnectionContext {

        public void openService(final String serviceType, final String groupName, final OptionMap optionMap, final ServiceResult serviceResult) {
            final String canonServiceType = serviceType.toLowerCase();
            final String canonGroupName = groupName.toLowerCase();
            final ConcurrentMap<String, ServiceRegistration> submap = registeredLocalServices.get(canonServiceType);
            if (submap == null) {
                serviceResult.notFound();
                return;
            }
            final ServiceRegistration registration;
            if (canonGroupName.equals("*")) {
                final Iterator<Map.Entry<String, ServiceRegistration>> iter = submap.entrySet().iterator();
                if (! iter.hasNext()) {
                    serviceResult.notFound();
                    return;
                }
                registration = iter.next().getValue();
            } else {
                registration = submap.get(canonGroupName);
                if (registration == null) {
                    serviceResult.notFound();
                    return;
                }
            }
            registration.getRequestHandlerConnector().createRequestHandler(new Result<RequestHandler>() {
                public void setResult(final RequestHandler result) {
                    serviceResult.opened(result, registration.getOptionMap());
                }

                public void setException(final IOException exception) {
                    log.warn(exception, "Unexpected exception on service lookup");
                    serviceResult.notFound();
                }

                public void setCancelled() {
                    log.warn("Unexpected cancellation on service lookup");
                    serviceResult.notFound();
                }
            });
        }
    }

    private class ConnectionImpl extends AbstractHandleableCloseable<Connection> implements Connection {
        private final ConnectionHandler connectionHandler;

        private ConnectionImpl(final ConnectionHandlerFactory connectionHandlerFactory) {
            super(EndpointImpl.this.executor);
            connectionHandler = connectionHandlerFactory.createInstance(localConnectionContext);
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

    private final class ConnectionProviderContextImpl extends AbstractHandleableCloseable<ConnectionProviderContext> implements ConnectionProviderContext {

        private ConnectionProviderContextImpl() {
            super(executor);
        }

        public void accept(final ConnectionHandlerFactory connectionHandlerFactory) {
            connectionHandlerFactory.createInstance(localConnectionContext);
        }
    }

    private final ConnectionContext localConnectionContext = new LocalConnectionContext();
    private final ConnectionHandler loopbackConnectionHandler = new LoopbackConnectionHandler();
    private final Connection loopbackConnection = new LoopbackConnection();

    // todo - move this into XNIO IoUtils
    private static class NullCancellable implements Cancellable {
        public Cancellable cancel() {
            return this;
        }
    }

    private final class LocalConnectionProvider implements ConnectionProvider<Void> {

        public Cancellable connect(final URI uri, final OptionMap connectOptions, final Result<ConnectionHandlerFactory> result) throws IllegalArgumentException {
            result.setResult(new ConnectionHandlerFactory() {
                public ConnectionHandler createInstance(final ConnectionContext context) {
                    return loopbackConnectionHandler;
                }

            });
            return NULL_CANCELLABLE;
        }

        public Void getProviderInterface() {
            return null;
        }
    }

    private class LoopbackConnection implements Connection {

        public <I, O> IoFuture<? extends Client<I, O>> openClient(final String serviceType, final String groupName, final Class<I> requestClass, final Class<O> replyClass) {
            final IoFuture.Manager<Client<I, O>> mgr = new IoFuture.Manager<Client<I, O>>();
            mgr.addCancelHandler(loopbackConnectionHandler.open(serviceType, groupName, new Result<RequestHandler>() {
                public void setResult(final RequestHandler result) {
                    mgr.setResult(ClientImpl.create(result, executor, requestClass, replyClass));
                }

                public void setException(final IOException exception) {
                    mgr.setException(exception);
                }

                public void setCancelled() {
                    mgr.finishCancel();
                }
            }));
            return mgr.getIoFuture();
        }

        public <I, O> ClientConnector<I, O> createClientConnector(final RequestListener<I, O> listener, final Class<I> requestClass, final Class<O> replyClass) {
            final Client<I, O> client;
            final ClientContextImpl context = new ClientContextImpl(executor, LoopbackConnection.this);
            try {
                client = createClient(createLocalRequestHandler(listener, requestClass, replyClass), requestClass, replyClass);
                context.addCloseHandler(new CloseHandler<ClientContext>() {
                    public void handleClose(final ClientContext closed) {
                        IoUtils.safeClose(client);
                    }
                });
                return new LoopbackClientConnector<I, O>(new FinishedIoFuture<Client<I, O>>(client), context);
            } catch (IOException e) {
                return new LoopbackClientConnector<I, O>(new FailedIoFuture<Client<I, O>>(e), context);
            }
        }

        public void close() {
            // ignored
        }

        public Key addCloseHandler(final CloseHandler<? super Connection> closeHandler) {
            return EndpointImpl.this.addCloseHandler(new CloseHandler<Endpoint>() {
                public void handleClose(final Endpoint closed) {
                    closeHandler.handleClose(LoopbackConnection.this);
                }
            });
        }
    }

    private static class LoopbackClientConnector<I, O> implements ClientConnector<I, O> {

        private final IoFuture<Client<I, O>> ioFuture;
        private final ClientContextImpl context;

        public LoopbackClientConnector(final IoFuture<Client<I, O>> ioFuture, final ClientContextImpl context) {
            this.ioFuture = ioFuture;
            this.context = context;
        }

        public IoFuture<? extends Client<I, O>> getFutureClient() throws SecurityException {
            return ioFuture;
        }

        public ClientContext getClientContext() throws SecurityException {
            return context;
        }
    }

    private class LoopbackConnectionHandler implements ConnectionHandler {

        public Cancellable open(final String serviceType, final String groupName, final Result<RequestHandler> result) {
            localConnectionContext.openService(serviceType, groupName, OptionMap.EMPTY, new ConnectionContext.ServiceResult() {
                public void opened(final RequestHandler requestHandler, final OptionMap optionMap) {
                    result.setResult(requestHandler);
                }

                public void notFound() {
                    result.setException(new ServiceNotFoundException(ServiceURI.create(serviceType, groupName, name), "No such service located"));
                }
            });
            return NULL_CANCELLABLE;
        }

        public RequestHandlerConnector createConnector(final RequestHandler localHandler) {
            // the loopback connection just returns the local handler directly as no forwarding is involved
            return new RequestHandlerConnector() {
                public Cancellable createRequestHandler(final Result<RequestHandler> result) throws SecurityException {
                    result.setResult(localHandler);
                    return NULL_CANCELLABLE;
                }
            };
        }

        public void close() {
            // not closeable
        }
    }
}
