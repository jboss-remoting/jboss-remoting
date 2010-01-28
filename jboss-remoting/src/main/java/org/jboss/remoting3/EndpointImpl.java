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
import org.jboss.remoting3.spi.ConnectionProviderRegistration;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ProtocolServiceType;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.FailedIoFuture;
import org.jboss.xnio.FinishedIoFuture;
import org.jboss.xnio.FutureResult;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Result;
import org.jboss.xnio.TranslatingResult;
import org.jboss.xnio.WeakCloseable;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.TextOutputCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;

/**
 *
 */
final class EndpointImpl extends AbstractHandleableCloseable<Endpoint> implements Endpoint {

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

    /**
     * The name of this endpoint.
     */
    private final String name;

    /**
     * Snapshot lock.  Hold this lock while reading or updating {@link #serviceListenerRegistrations} or while updating
     * {@link #registeredLocalServices}.  Allows atomic snapshot of existing service registrations and listener add.
     */
    private final Object serviceRegistrationLock = new Object();

    /**
     * The currently registered service listeners.  Protected by {@link #serviceRegistrationLock}.
     */
    private final ConcurrentMap<Registration, ServiceRegistrationListener> serviceListenerRegistrations = concurrentIdentityMap(serviceRegistrationLock);
    /**
     * The currently registered services.  Protected by {@link #serviceRegistrationLock}.
     */
    private final ConcurrentMap<String, ConcurrentMap<String, ServiceRegistration>> registeredLocalServices = concurrentMap(serviceRegistrationLock);
    /**
     * The currently registered connection providers.
     */
    private final ConcurrentMap<String, ConnectionProvider<?>> connectionProviders = concurrentMap();
    /**
     * The provider maps for the different protocol service types.
     */
    private final ConcurrentMap[] providerMaps = new ConcurrentMap[ProtocolServiceType.getServiceTypes().length];

    /**
     * The single per-endpoint connection provider context instance.
     */
    private final ConnectionProviderContext connectionProviderContext;
    /**
     * The special connection handler instance corresponding to {@link #loopbackConnection}.
     */
    private final ConnectionHandler loopbackConnectionHandler;
    /**
     * The special loopback connection instance for local request handlers which are created via {@link #createLocalRequestHandler(RequestListener, Class, Class)} and
     * not tied to a service.  This connection cannot be closed.
     */
    private final Connection loopbackConnection;
    /**
     * The special connection handler context corresponding to {@link #loopbackConnectionHandler}.
     */
    private final ConnectionHandlerContext localConnectionContext;

    private static final EndpointPermission CREATE_REQUEST_HANDLER_PERM = new EndpointPermission("createRequestHandler");
    private static final EndpointPermission REGISTER_SERVICE_PERM = new EndpointPermission("registerService");
    private static final EndpointPermission CREATE_CLIENT_PERM = new EndpointPermission("createClient");
    private static final EndpointPermission ADD_SERVICE_LISTENER_PERM = new EndpointPermission("addServiceListener");
    private static final EndpointPermission CONNECT_PERM = new EndpointPermission("connect");
    private static final EndpointPermission ADD_CONNECTION_PROVIDER_PERM = new EndpointPermission("addConnectionProvider");
    private static final EndpointPermission ADD_MARSHALLING_PROTOCOL_PERM = new EndpointPermission("addMarshallingProtocol");
    private static final EndpointPermission GET_CONNECTION_PROVIDER_INTERFACE_PERM = new EndpointPermission("getConnectionProviderInterface");

    EndpointImpl(final Executor executor, final String name) {
        super(executor);
        for (int i = 0; i < providerMaps.length; i++) {
            providerMaps[i] = concurrentMap();
        }
        this.executor = executor;
        this.name = name;
        connectionProviders.put("local", new LocalConnectionProvider());
        connectionProviderContext = new ConnectionProviderContextImpl();
        loopbackConnectionHandler = new LoopbackConnectionHandler();
        loopbackConnection = new LoopbackConnection();
        localConnectionContext = new LocalConnectionContext(null, loopbackConnection);
    }

    private final Executor executor;

    protected Executor getOrderedExecutor() {
        return new OrderedExecutor(executor);
    }

    protected Executor getExecutor() {
        return executor;
    }

    @SuppressWarnings({ "unchecked" })
    private <T> ConcurrentMap<String, T> getMapFor(ProtocolServiceType<T> type) {
        return (ConcurrentMap<String, T>)providerMaps[type.getIndex()];
    }

    public String getName() {
        return name;
    }

    public <I, O> RequestHandler createLocalRequestHandler(final RequestListener<? super I, ? extends O> requestListener, final Class<I> requestClass, final Class<O> replyClass) throws IOException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_REQUEST_HANDLER_PERM);
        }
        checkOpen();
        final ClientContextImpl clientContext = new ClientContextImpl(executor, loopbackConnection);
        final LocalRequestHandler<I, O> localRequestHandler = new LocalRequestHandler<I, O>(executor, requestListener, clientContext, requestClass, replyClass);
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

    public ServiceBuilder<?, ?> serviceBuilder() {
        return new ServiceBuilderImpl<Void, Void>();
    }

    private final class ServiceBuilderImpl<I, O> implements ServiceBuilder<I, O> {
        private String groupName;
        private String serviceType;
        private Class<I> requestType;
        private Class<O> replyType;
        private ClientListener<? super I, ? extends O> clientListener;
        private ClassLoader classLoader;
        private OptionMap optionMap = OptionMap.EMPTY;

        public ServiceBuilder<I, O> setGroupName(final String groupName) {
            this.groupName = groupName;
            return this;
        }

        public ServiceBuilder<I, O> setServiceType(final String serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        @SuppressWarnings({ "unchecked" })
        public <N> ServiceBuilder<N, O> setRequestType(final Class<N> newRequestType) {
            if (newRequestType == null) {
                throw new NullPointerException("newRequestType is null");
            }
            clientListener = null;
            ServiceBuilderImpl<N, O> castBuilder = (ServiceBuilderImpl<N, O>) this;
            castBuilder.requestType = newRequestType;
            return castBuilder;
        }

        @SuppressWarnings({ "unchecked" })
        public <N> ServiceBuilder<I, N> setReplyType(final Class<N> newReplyType) {
            if (newReplyType == null) {
                throw new NullPointerException("newReplyType is null");
            }
            clientListener = null;
            ServiceBuilderImpl<I, N> castBuilder = (ServiceBuilderImpl<I, N>) this;
            castBuilder.replyType = newReplyType;
            return castBuilder;
        }

        public ServiceBuilder<I, O> setClientListener(final ClientListener<? super I, ? extends O> clientListener) {
            if (requestType == null || replyType == null) {
                throw new IllegalArgumentException("Must configure both request and reply type before setting the client listener");
            }
            this.clientListener = clientListener;
            return this;
        }

        public ServiceBuilder<I, O> setClassLoader(final ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        public ServiceBuilder<I, O> setOptionMap(final OptionMap optionMap) {
            if (optionMap == null) {
                throw new NullPointerException("optionMap is null");
            }
            this.optionMap = optionMap;
            return this;
        }

        public Registration register() throws IOException {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPermission(REGISTER_SERVICE_PERM);
            }
            if (groupName == null) {
                throw new NullPointerException("groupName is null");
            }
            if (serviceType == null) {
                throw new NullPointerException("serviceType is null");
            }
            if (requestType == null) {
                throw new NullPointerException("requestType is null");
            }
            if (replyType == null) {
                throw new NullPointerException("replyType is null");
            }
            if (clientListener == null) {
                throw new NullPointerException("clientListener is null");
            }
            final Integer metric = optionMap.get(RemotingOptions.METRIC);
            if (metric != null && metric.intValue() < 0) {
                throw new IllegalArgumentException("metric must be greater than or equal to zero");
            }
            ServiceURI.validateServiceType(serviceType);
            ServiceURI.validateGroupName(groupName);
            checkOpen();
            final String canonServiceType = serviceType.toLowerCase();
            final String canonGroupName = groupName.toLowerCase();
            final Executor executor = EndpointImpl.this.executor;
            final ConcurrentMap<String, ConcurrentMap<String, ServiceRegistration>> registeredLocalServices = EndpointImpl.this.registeredLocalServices;
            final RequestHandlerConnector requestHandlerConnector = new Connector();
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
                serviceListenerRegistrations = EndpointImpl.this.serviceListenerRegistrations.entrySet().iterator();
            }
            // notify all service listener registrations that were registered at the time the service was created
            final ServiceRegistrationListener.ServiceInfo serviceInfo = new ServiceRegistrationListener.ServiceInfo();
            serviceInfo.setGroupName(groupName);
            serviceInfo.setServiceType(serviceType);
            serviceInfo.setOptionMap(optionMap);
            serviceInfo.setRegistrationHandle(handle);
            serviceInfo.setRequestHandlerConnector(requestHandlerConnector);
            serviceInfo.setRequestClass(requestType);
            serviceInfo.setReplyClass(replyType);
            final ClassLoader classLoader = this.classLoader;
            serviceInfo.setServiceClassLoader(classLoader == null ? clientListener.getClass().getClassLoader() : classLoader);
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

        private class Connector implements RequestHandlerConnector {

            Connector() {
            }

            public Cancellable createRequestHandler(final Result<RequestHandler> result) throws SecurityException {
                try {
                    final ClientContextImpl clientContext = new ClientContextImpl(executor, loopbackConnection);
                    final RequestHandler localRequestHandler = createLocalRequestHandler(clientListener.handleClientOpen(clientContext), requestType, replyType);
                    clientContext.addCloseHandler(new CloseHandler<ClientContext>() {
                        public void handleClose(final ClientContext closed) {
                            IoUtils.safeClose(localRequestHandler);
                        }
                    });
                    result.setResult(localRequestHandler);
                } catch (IOException e) {
                    result.setException(e);
                }
                return IoUtils.nullCancellable();
            }
        }
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
        return connect(destination, connectOptions, new DefaultCallbackHandler(connectOptions.get(RemotingOptions.AUTH_USER_NAME), connectOptions.get(RemotingOptions.AUTH_REALM), null));
    }

    public IoFuture<? extends Connection> connect(final URI destination, final OptionMap connectOptions, final CallbackHandler callbackHandler) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONNECT_PERM);
        }
        final String scheme = destination.getScheme();
        final ConnectionProvider<?> connectionProvider = connectionProviders.get(scheme);
        if (connectionProvider == null) {
            throw new UnknownURISchemeException("No connection provider for URI scheme \"" + scheme + "\" is installed");
        }
        final FutureResult<Connection> futureResult = new FutureResult<Connection>(executor);
        futureResult.addCancelHandler(connectionProvider.connect(destination, connectOptions, new TranslatingResult<ConnectionHandlerFactory, Connection>(futureResult) {
            protected Connection translate(final ConnectionHandlerFactory input) {
                return new ConnectionImpl(input, connectionProviderContext);
            }
        }, callbackHandler));
        return futureResult.getIoFuture();
    }

    public IoFuture<? extends Connection> connect(final URI destination, final OptionMap connectOptions, final String userName, final String realmName, final char[] password) throws IOException {
        final String actualUserName = userName != null ? userName : connectOptions.get(RemotingOptions.AUTH_USER_NAME);
        final String actualUserRealm = realmName != null ? realmName : connectOptions.get(RemotingOptions.AUTH_REALM);
        return connect(destination, connectOptions, new DefaultCallbackHandler(actualUserName, actualUserRealm, password));
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

    public <T> T getConnectionProviderInterface(final String uriScheme, final Class<T> expectedType) throws UnknownURISchemeException, ClassCastException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(GET_CONNECTION_PROVIDER_INTERFACE_PERM);
        }
        final ConnectionProvider<?> provider = connectionProviders.get(uriScheme);
        if (provider == null) {
            throw new UnknownURISchemeException("No connection provider for URI scheme \"" + uriScheme + "\" is installed");
        }
        return expectedType.cast(provider.getProviderInterface());
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

    public <T> Registration addProtocolService(final ProtocolServiceType<T> type, final String name, final T provider) throws DuplicateRegistrationException {
        return addMarshallingRegistration(name, provider, getMapFor(type), type.getDescription());
    }

    public String toString() {
        return "endpoint \"" + name + "\" <" + Integer.toHexString(hashCode()) + ">";
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

    private final class LocalConnectionContext implements ConnectionHandlerContext {
        private final ConnectionProviderContext connectionProviderContext;
        private final Connection connection;

        LocalConnectionContext(final ConnectionProviderContext connectionProviderContext, final Connection connection) {
            this.connectionProviderContext = connectionProviderContext;
            this.connection = connection;
        }

        public ConnectionProviderContext getConnectionProviderContext() {
            return connectionProviderContext;
        }

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
                public boolean setResult(final RequestHandler result) {
                    serviceResult.opened(result, registration.getOptionMap());
                    return true;
                }

                public boolean setException(final IOException exception) {
                    log.warn(exception, "Unexpected exception on service lookup");
                    serviceResult.notFound();
                    return true;
                }

                public boolean setCancelled() {
                    log.warn("Unexpected cancellation on service lookup");
                    serviceResult.notFound();
                    return true;
                }
            });
        }

        public void remoteClosed() {
            IoUtils.safeClose(connection);
        }
    }

    private class ConnectionImpl extends AbstractHandleableCloseable<Connection> implements Connection {
        private final ConnectionHandler connectionHandler;

        private ConnectionImpl(final ConnectionHandlerFactory connectionHandlerFactory, final ConnectionProviderContext connectionProviderContext) {
            super(EndpointImpl.this.executor);
            connectionHandler = connectionHandlerFactory.createInstance(new LocalConnectionContext(connectionProviderContext, this));
        }

        protected void closeAction() throws IOException {
            connectionHandler.close();
        }

        public <I, O> IoFuture<? extends Client<I, O>> openClient(final String serviceType, final String groupName, final Class<I> requestClass, final Class<O> replyClass) {
            final FutureResult<Client<I, O>> futureResult = new FutureResult<Client<I, O>>();
            futureResult.addCancelHandler(connectionHandler.open(serviceType, groupName, new TranslatingResult<RequestHandler, Client<I, O>>(futureResult) {
                protected Client<I, O> translate(final RequestHandler input) throws IOException {
                    return createClient(input, requestClass, replyClass);
                }
            }));
            return futureResult.getIoFuture();
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

        public Executor getExecutor() {
            return super.getExecutor();
        }

        public void accept(final ConnectionHandlerFactory connectionHandlerFactory) {
            connectionHandlerFactory.createInstance(localConnectionContext);
        }

        public <T> Iterable<Map.Entry<String, T>> getProtocolServiceProviders(final ProtocolServiceType<T> serviceType) {
            return getMapFor(serviceType).entrySet();
        }

        public <T> T getProtocolServiceProvider(final ProtocolServiceType<T> serviceType, final String name) {
            return getMapFor(serviceType).get(name);
        }
    }

    private final class LocalConnectionProvider implements ConnectionProvider<Void> {

        public Cancellable connect(final URI uri, final OptionMap connectOptions, final Result<ConnectionHandlerFactory> result, final CallbackHandler callbackHandler) throws IllegalArgumentException {
            result.setResult(new ConnectionHandlerFactory() {
                public ConnectionHandler createInstance(final ConnectionHandlerContext context) {
                    return loopbackConnectionHandler;
                }
            });
            return IoUtils.nullCancellable();
        }

        public Void getProviderInterface() {
            return null;
        }
    }

    private class LoopbackConnection implements Connection {

        public <I, O> IoFuture<? extends Client<I, O>> openClient(final String serviceType, final String groupName, final Class<I> requestClass, final Class<O> replyClass) {
            final FutureResult<Client<I,O>> futureResult = new FutureResult<Client<I, O>>(executor);
            futureResult.addCancelHandler(loopbackConnectionHandler.open(serviceType, groupName, new TranslatingResult<RequestHandler, Client<I, O>>(futureResult) {
                protected Client<I, O> translate(final RequestHandler input) throws IOException {
                    return ClientImpl.create(input, executor, requestClass, replyClass);
                }
            }));
            return futureResult.getIoFuture();
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

        public Cancellable open(final String serviceType, final String groupName, final org.jboss.xnio.Result<RequestHandler> result) {
            localConnectionContext.openService(serviceType, groupName, OptionMap.EMPTY, new ConnectionHandlerContext.ServiceResult() {
                public void opened(final RequestHandler requestHandler, final OptionMap optionMap) {
                    result.setResult(requestHandler);
                }

                public void notFound() {
                    result.setException(new ServiceNotFoundException(ServiceURI.create(serviceType, groupName, name), "No such service located"));
                }
            });
            return IoUtils.nullCancellable();
        }

        public RequestHandlerConnector createConnector(final RequestHandler localHandler) {
            // the loopback connection just returns the local handler directly as no forwarding is involved
            return new RequestHandlerConnector() {
                public Cancellable createRequestHandler(final org.jboss.xnio.Result<RequestHandler> result) throws SecurityException {
                    result.setResult(localHandler);
                    return IoUtils.nullCancellable();
                }
            };
        }

        public void close() {
            // not closeable
        }
    }

    private static class DefaultCallbackHandler implements CallbackHandler {

        private final String actualUserName;
        private final String actualUserRealm;
        private final char[] password;

        private DefaultCallbackHandler(final String actualUserName, final String actualUserRealm, final char[] password) {
            this.actualUserName = actualUserName;
            this.actualUserRealm = actualUserRealm;
            this.password = password;
        }

        public void handle(final Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            MAIN: for (Callback callback : callbacks) {
                if (callback instanceof NameCallback && actualUserName != null) {
                    final NameCallback nameCallback = (NameCallback) callback;
                    nameCallback.setName(actualUserName);
                } else if (callback instanceof RealmCallback && actualUserRealm != null) {
                    final RealmCallback realmCallback = (RealmCallback) callback;
                    realmCallback.setText(actualUserRealm);
                } else if (callback instanceof RealmChoiceCallback && actualUserRealm != null) {
                    final RealmChoiceCallback realmChoiceCallback = (RealmChoiceCallback) callback;
                    final String[] choices = realmChoiceCallback.getChoices();
                    for (int i = 0; i < choices.length; i++) {
                        if (choices[i].equals(actualUserRealm)) {
                            realmChoiceCallback.setSelectedIndex(i);
                            continue MAIN;
                        }
                    }
                    throw new UnsupportedCallbackException(callback, "No realm choices match realm '" + actualUserRealm + "'");
                } else if (callback instanceof TextOutputCallback) {
                    final TextOutputCallback textOutputCallback = (TextOutputCallback) callback;
                    final String kind;
                    switch (textOutputCallback.getMessageType()) {
                        case TextOutputCallback.ERROR: kind = "ERROR"; break;
                        case TextOutputCallback.INFORMATION: kind = "INFORMATION"; break;
                        case TextOutputCallback.WARNING: kind = "WARNING"; break;
                        default: kind = "UNKNOWN"; break;
                    }
                    log.debug("Authentication layer produced a %s message: %s", kind, textOutputCallback.getMessage());
                } else if (callback instanceof PasswordCallback && password != null) {
                    final PasswordCallback passwordCallback = (PasswordCallback) callback;
                    passwordCallback.setPassword(password);
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }
}
