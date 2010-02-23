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
import java.util.Random;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jboss.marshalling.util.IntKeyMap;
import org.jboss.remoting3.security.RemotingPermission;
import org.jboss.remoting3.security.SimpleClientCallbackHandler;
import org.jboss.remoting3.service.Services;
import org.jboss.remoting3.service.locator.ServiceReply;
import org.jboss.remoting3.service.locator.ServiceRequest;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.ConnectionProviderRegistration;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ProtocolServiceType;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.FutureResult;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.TranslatingResult;
import org.jboss.xnio.WeakCloseable;

import javax.security.auth.callback.CallbackHandler;

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

    private final Attachments attachments = new AttachmentsImpl();

    /**
     * The name of this endpoint.
     */
    private final String name;

    private final OptionMap optionMap;

    private final Lock serviceWriteLock;
    private final Lock serviceReadLock;

    /**
     * The currently registered service listeners.  Protected by {@link #serviceWriteLock}.
     */
    private final Map<Registration, ServiceRegistrationListener> serviceListenerRegistrations = hashMap();
    /**
     * The currently registered services.  Protected by {@link #serviceWriteLock}.
     */
    private final Map<String, Map<String, ServiceRegistrationInfo>> localServiceIndex = hashMap();
    private final IntKeyMap<ServiceRegistrationInfo> localServiceTable = new IntKeyMap<ServiceRegistrationInfo>();
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

    private static final RemotingPermission CREATE_REQUEST_HANDLER_PERM = new RemotingPermission("createRequestHandler");
    private static final RemotingPermission REGISTER_SERVICE_PERM = new RemotingPermission("registerService");
    private static final RemotingPermission CREATE_CLIENT_PERM = new RemotingPermission("createClient");
    private static final RemotingPermission ADD_SERVICE_LISTENER_PERM = new RemotingPermission("addServiceListener");
    private static final RemotingPermission CONNECT_PERM = new RemotingPermission("connect");
    private static final RemotingPermission ADD_CONNECTION_PROVIDER_PERM = new RemotingPermission("addConnectionProvider");
    private static final RemotingPermission ADD_MARSHALLING_PROTOCOL_PERM = new RemotingPermission("addMarshallingProtocol");
    private static final RemotingPermission GET_CONNECTION_PROVIDER_INTERFACE_PERM = new RemotingPermission("getConnectionProviderInterface");

    EndpointImpl(final Executor executor, final String name, final OptionMap optionMap) throws IOException {
        super(executor);
        final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
        serviceWriteLock = rwl.writeLock();
        serviceReadLock = rwl.readLock();
        for (int i = 0; i < providerMaps.length; i++) {
            providerMaps[i] = concurrentMap();
        }
        this.executor = executor;
        this.name = name;
        connectionProviderContext = new ConnectionProviderContextImpl();
        // add local connection provider
        connectionProviders.put("local", new LocalConnectionProvider(connectionProviderContext));
        // add default services
        serviceBuilder(ServiceRequest.class, ServiceReply.class)
                .setOptionMap(OptionMap.builder().set(RemotingOptions.FIXED_SERVICE_ID, Services.LOCATE_SERVICE).getMap())
                .setClientListener(new ServiceLocationListener())
                .setServiceType("org.jboss.remoting.service.locate")
                .setGroupName("default")
                .register();
        this.optionMap = optionMap;
    }

    private final Executor executor;

    protected Executor getOrderedExecutor() {
        return new OrderedExecutor(executor);
    }

    protected Executor getExecutor() {
        return executor;
    }

    public Attachments getAttachments() {
        return attachments;
    }

    @SuppressWarnings({ "unchecked" })
    private <T> ConcurrentMap<String, T> getMapFor(ProtocolServiceType<T> type) {
        return (ConcurrentMap<String, T>)providerMaps[type.getIndex()];
    }

    public String getName() {
        return name;
    }

    public void close() throws IOException {
        if (! optionMap.contains(Remoting.UNCLOSEABLE)) {
            super.close();
        }
    }

    public <I, O> RequestHandler createLocalRequestHandler(final RequestListener<? super I, ? extends O> requestListener, final Class<I> requestClass, final Class<O> replyClass) throws IOException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_REQUEST_HANDLER_PERM);
        }
        checkOpen();
        final ClientContextImpl clientContext = new ClientContextImpl(executor, null);
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

    public <I, O> ServiceBuilder<I, O> serviceBuilder(final Class<I> requestClass, final Class<O> replyClass) {
        return serviceBuilder().setRequestType(requestClass).setReplyType(replyClass);
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
            Lock lock = serviceWriteLock;
            lock.lock();
            try {
                final int slot;
                final Integer fixedServiceId = optionMap.get(RemotingOptions.FIXED_SERVICE_ID);
                if (fixedServiceId != null) {
                    slot = fixedServiceId.intValue();
                    if (slot < 0) {
                        throw new IllegalArgumentException("fixed service ID must be greater than or equal to zero");
                    }
                    // todo - check permission for fixed service ID
                    if (localServiceTable.containsKey(slot)) {
                        throw new DuplicateRegistrationException("A service with slot ID " + slot + " is already registered");
                    }
                } else {
                    int id;
                    do {
                        id = getRandomSlotID();
                    } while (localServiceTable.containsKey(id));
                    slot = id;
                }
                log.trace("Registering a service type '%s' group name '%s' with ID %d", serviceType, groupName, Integer.valueOf(slot));
                final String canonServiceType = serviceType.toLowerCase();
                final String canonGroupName = groupName.toLowerCase();
                final Executor executor = EndpointImpl.this.executor;
                final Map<String, Map<String, ServiceRegistrationInfo>> registeredLocalServices = localServiceIndex;
                final RequestHandlerFactory<I, O> handlerFactory = RequestHandlerFactory.create(executor, clientListener, requestType, replyType);
                final ServiceRegistrationInfo registration = new ServiceRegistrationInfo(serviceType, groupName, name, optionMap, handlerFactory, slot);
                // this handle is used to remove the service registration
                class ServiceRegistration extends AbstractHandleableCloseable<Registration> implements Registration {

                    ServiceRegistration() {
                        super(executor);
                    }

                    protected void closeAction() {
                        final Lock lock = serviceWriteLock;
                        lock.lock();
                        try {
                            final Map<String, ServiceRegistrationInfo> submap = localServiceIndex.get(serviceType);
                            if (submap != null) {
                                final ServiceRegistrationInfo oldReg = submap.get(groupName);
                                if (oldReg == registration) {
                                    submap.remove(groupName);
                                }
                            }
                            final int regSlot = slot;
                            final ServiceRegistrationInfo oldReg = localServiceTable.get(regSlot);
                            if (oldReg == registration) {
                                localServiceTable.remove(regSlot);
                            }
                            log.trace("Removed service type '%s' group name '%s' with ID %d", serviceType, groupName, Integer.valueOf(slot));
                        } finally {
                            lock.unlock();
                        }
                    }

                    public void close() {
                        try {
                            super.close();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
                final Registration handle = new ServiceRegistration();
                registration.setHandle(handle);
                // actually register the service, and while we have the lock, snag a copy of the registration listener list
                final Map<String, ServiceRegistrationInfo> submap;
                if (registeredLocalServices.containsKey(canonServiceType)) {
                    submap = registeredLocalServices.get(canonServiceType);
                    if (submap.containsKey(canonGroupName)) {
                        throw new ServiceRegistrationException("ListenerRegistration of service of type \"" + serviceType + "\" in group \"" + groupName + "\" duplicates an already-registered service's specification");
                    }
                } else {
                    submap = hashMap();
                    registeredLocalServices.put(canonServiceType, submap);
                }
                submap.put(canonGroupName, registration);
                localServiceTable.put(slot, registration);
                // downgrade safely to read lock
                final Lock readLock = serviceReadLock;
                //noinspection LockAcquiredButNotSafelyReleased
                readLock.lock();
                try {
                    lock.unlock();
                } finally {
                    lock = readLock;
                }
                // snapshot
                final Iterator<Map.Entry<Registration,ServiceRegistrationListener>> serviceListenerRegistrations;
                serviceListenerRegistrations = EndpointImpl.this.serviceListenerRegistrations.entrySet().iterator();
                // notify all service listener registrations that were registered at the time the service was created
                final ServiceRegistrationListener.ServiceInfo serviceInfo = new ServiceRegistrationListener.ServiceInfo();
                serviceInfo.setGroupName(groupName);
                serviceInfo.setServiceType(serviceType);
                serviceInfo.setOptionMap(optionMap);
                serviceInfo.setRegistrationHandle(handle);
                serviceInfo.setSlot(slot);
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
            } finally {
                lock.unlock();
            }
        }
    }

    private int getRandomSlotID() {
        final Random random = new Random();
        int id = random.nextInt() & 0x7fffffff;
        return id;
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
        class ServiceListenerRegistration extends AbstractHandleableCloseable<Registration> implements Registration {

            ServiceListenerRegistration() {
                super(executor);
            }

            protected void closeAction() {
                Lock lock = serviceWriteLock;
                lock.lock();
                try {
                    serviceListenerRegistrations.remove(this);
                } finally {
                    lock.unlock();
                }
            }

            public void close() {
                try {
                    super.close();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        final Registration registration = new ServiceListenerRegistration();
        Lock lock = serviceWriteLock;
        lock.lock();
        try {
            serviceListenerRegistrations.put(registration, listener);
            if (flags == null || ! flags.contains(ListenerFlag.INCLUDE_OLD)) {
                final Lock readLock = serviceReadLock;
                // safe downgrade
                //noinspection LockAcquiredButNotSafelyReleased
                readLock.lock();
                try {
                    lock.unlock();
                } finally {
                    lock = readLock;
                }
                final Executor executor = this.executor;
                for (final Map.Entry<String, Map<String, ServiceRegistrationInfo>> entry : localServiceIndex.entrySet()) {
                    for (final ServiceRegistrationInfo service : entry.getValue().values()) {
                        executor.execute(new Runnable() {
                            public void run() {
                                final ServiceRegistrationListener.ServiceInfo serviceInfo = new ServiceRegistrationListener.ServiceInfo();
                                serviceInfo.setGroupName(service.getGroupName());
                                serviceInfo.setOptionMap(service.getOptionMap());
                                serviceInfo.setRegistrationHandle(service.getHandle());
                                serviceInfo.setSlot(service.getSlot());
                                serviceInfo.setServiceType(service.getServiceType());
                                try {
                                    listener.serviceRegistered(registration, serviceInfo);
                                } catch (Throwable t) {
                                    logListenerError(t);
                                }
                            }
                        });
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return registration;
    }

    public IoFuture<? extends Connection> connect(final URI destination, final OptionMap connectOptions) throws IOException {
        return connect(destination, connectOptions, new SimpleClientCallbackHandler(connectOptions.get(RemotingOptions.AUTH_USER_NAME), connectOptions.get(RemotingOptions.AUTH_REALM), null));
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
                return new ConnectionImpl(EndpointImpl.this, input, connectionProviderContext, destination.toString());
            }
        }, callbackHandler));
        return futureResult.getIoFuture();
    }

    public IoFuture<? extends Connection> connect(final URI destination, final OptionMap connectOptions, final String userName, final String realmName, final char[] password) throws IOException {
        final String actualUserName = userName != null ? userName : connectOptions.get(RemotingOptions.AUTH_USER_NAME);
        final String actualUserRealm = realmName != null ? realmName : connectOptions.get(RemotingOptions.AUTH_REALM);
        return connect(destination, connectOptions, new SimpleClientCallbackHandler(actualUserName, actualUserRealm, password));
    }

    public <T> ConnectionProviderRegistration<T> addConnectionProvider(final String uriScheme, final ConnectionProviderFactory<T> providerFactory) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_CONNECTION_PROVIDER_PERM);
        }
        final ConnectionProviderContextImpl context = new ConnectionProviderContextImpl();
        final ConnectionProvider<T> provider = providerFactory.createInstance(context);
        if (connectionProviders.putIfAbsent(uriScheme, provider) != null) {
            throw new DuplicateRegistrationException("URI scheme '" + uriScheme + "' is already registered to a provider");
        }
        final ConnectionProviderRegistration<T> handle = new ConnectionProviderRegistrationImpl<T>(uriScheme, provider);
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

    private class MapRegistration<T> extends AbstractHandleableCloseable<Registration> implements Registration {

        private final ConcurrentMap<String, T> map;
        private final String key;
        private final T value;

        private MapRegistration(final ConcurrentMap<String, T> map, final String key, final T value) {
            super(executor);
            this.map = map;
            this.key = key;
            this.value = value;
        }

        protected void closeAction() {
            map.remove(key, value);
        }

        public void close() {
            try {
                super.close();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    final class LocalConnectionContext implements ConnectionHandlerContext {
        private final ConnectionProviderContext connectionProviderContext;
        private final Connection connection;

        LocalConnectionContext(final ConnectionProviderContext connectionProviderContext, final Connection connection) {
            this.connectionProviderContext = connectionProviderContext;
            this.connection = connection;
        }

        public ConnectionProviderContext getConnectionProviderContext() {
            return connectionProviderContext;
        }

        public RequestHandler openService(final int slotId, final OptionMap optionMap) throws ServiceOpenException {
            serviceReadLock.lock();
            try {
                final ServiceRegistrationInfo info = localServiceTable.get(slotId);
                if (info == null) {
                    throw new ServiceOpenException("No service with ID of " + slotId);
                }
                return info.getRequestHandlerFactory().createRequestHandler(connection);
            } finally {
                serviceReadLock.unlock();
            }
        }

        public void remoteClosed() {
            IoUtils.safeClose(connection);
        }
    }

    private final class ConnectionProviderContextImpl implements ConnectionProviderContext {

        private ConnectionProviderContextImpl() {
        }

        public Executor getExecutor() {
            return executor;
        }

        public void accept(final ConnectionHandlerFactory connectionHandlerFactory) {
            connectionHandlerFactory.createInstance(new LocalConnectionContext(connectionProviderContext, new ConnectionImpl(EndpointImpl.this, connectionHandlerFactory, this, "client")));
        }

        public <T> Iterable<Map.Entry<String, T>> getProtocolServiceProviders(final ProtocolServiceType<T> serviceType) {
            return getMapFor(serviceType).entrySet();
        }

        public <T> T getProtocolServiceProvider(final ProtocolServiceType<T> serviceType, final String name) {
            return getMapFor(serviceType).get(name);
        }
    }

    private class ConnectionProviderRegistrationImpl<T> extends AbstractHandleableCloseable<Registration> implements ConnectionProviderRegistration<T> {

        private final String uriScheme;
        private final ConnectionProvider<T> provider;

        public ConnectionProviderRegistrationImpl(final String uriScheme, final ConnectionProvider<T> provider) {
            super(executor);
            this.uriScheme = uriScheme;
            this.provider = provider;
        }

        protected void closeAction() {
            connectionProviders.remove(uriScheme, provider);
        }

        public void close() {
            try {
                super.close();
            } catch (IOException e) {
                // not possible
                throw new IllegalStateException(e);
            }
        }

        public T getProviderInterface() {
            return provider.getProviderInterface();
        }
    }

    final class ServiceLocationListener implements ClientListener<ServiceRequest, ServiceReply> {
        private final RequestListener<ServiceRequest, ServiceReply> requestListener = new RequestListener<ServiceRequest, ServiceReply>() {
            public void handleRequest(final RequestContext<ServiceReply> context, final ServiceRequest request) throws RemoteExecutionException {
                final String requestGroupName = request.getGroupName();
                final String requestServiceType = request.getServiceType();
                final Lock lock = serviceReadLock;
                lock.lock();
                try {
                    final Map<String, ServiceRegistrationInfo> submap = localServiceIndex.get(requestServiceType);
                    if (submap != null) {
                        final ServiceRegistrationInfo info;
                        if (requestGroupName == null || "*".equals(requestGroupName)) {
                            final Iterator<Map.Entry<String,ServiceRegistrationInfo>> i = submap.entrySet().iterator();
                            if (i.hasNext()) {
                                final Map.Entry<String, ServiceRegistrationInfo> entry = i.next();
                                info = entry.getValue();
                            } else {
                                info = null;
                            }
                        } else {
                            info = submap.get(requestGroupName);
                        }
                        if (info != null) {
                            try {
                                context.sendReply(ServiceReply.create(info.getSlot(), info.getRequestHandlerFactory().getRequestClass(), info.getRequestHandlerFactory().getReplyClass()));
                            } catch (IOException e) {
                                log.trace("Failed to send service reply: %s", e);
                                // reply failed
                            }
                            return;
                        }
                    }
                    throw new RemoteExecutionException(new ServiceNotFoundException(ServiceURI.create(requestServiceType, requestGroupName, null)));
                } finally {
                    lock.unlock();
                }
            }

            public void handleClose() {
            }
        };

        public RequestListener<ServiceRequest, ServiceReply> handleClientOpen(final ClientContext clientContext) {
            return requestListener;
        }
    }
}
