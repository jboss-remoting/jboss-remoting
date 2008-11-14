package org.jboss.remoting.core;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jboss.remoting.Client;
import org.jboss.remoting.ClientSource;
import org.jboss.remoting.CloseHandler;
import org.jboss.remoting.Endpoint;
import org.jboss.remoting.RequestListener;
import org.jboss.remoting.ServiceListener;
import org.jboss.remoting.SimpleCloseable;
import org.jboss.remoting.LocalServiceConfiguration;
import org.jboss.remoting.EndpointPermission;
import org.jboss.remoting.RemoteServiceConfiguration;
import org.jboss.remoting.spi.AbstractSimpleCloseable;
import org.jboss.remoting.spi.Handle;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.remoting.util.CollectionUtil;
import org.jboss.remoting.util.NamingThreadFactory;
import org.jboss.remoting.util.OrderedExecutorFactory;
import org.jboss.remoting.util.ServiceURI;
import org.jboss.remoting.version.Version;
import org.jboss.xnio.FailedIoFuture;
import org.jboss.xnio.FinishedIoFuture;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class EndpointImpl implements Endpoint {

    static {
        // Print Remoting "greeting" message
        Logger.getLogger("org.jboss.remoting").info("JBoss Remoting version %s", Version.VERSION);
    }

    private static final Logger log = Logger.getLogger("org.jboss.remoting.endpoint");

    private String name;

    private OrderedExecutorFactory orderedExecutorFactory;
    private ExecutorService executorService;

    private final Set<Closeable> resources = CollectionUtil.synchronizedWeakHashSet();
    private final ConcurrentMap<Object, Object> endpointMap = CollectionUtil.concurrentMap();

    private final Object serviceLock = new Object();
    private final Map<Object, ServiceListenerRegistration> serviceListenerMap = CollectionUtil.hashMap();
    private final Set<ServiceRegistration> serviceRegistrations = CollectionUtil.hashSet();

    private static final EndpointPermission CREATE_REQUEST_HANDLER_PERM = new EndpointPermission("createRequestHandler");
    private static final EndpointPermission REGISTER_SERVICE_PERM = new EndpointPermission("registerService");
    private static final EndpointPermission CREATE_CLIENT_PERM = new EndpointPermission("createClient");
    private static final EndpointPermission CREATE_CLIENT_SOURCE_PERM = new EndpointPermission("createClientSource");
    private static final EndpointPermission REGISTER_REMOTE_SERVICE_PERM = new EndpointPermission("registerRemoteService");
    private static final EndpointPermission ADD_SERVICE_LISTENER_PERM = new EndpointPermission("addServiceListener");

    public EndpointImpl() {
    }

    // Dependencies

    private Executor executor;

    public Executor getExecutor() {
        return executor;
    }

    Executor getOrderedExecutor() {
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

    // Lifecycle

    public void start() {
        // todo security check
        if (executor == null) {
            executor = executorService = Executors.newCachedThreadPool(new NamingThreadFactory(Executors.defaultThreadFactory(), "Remoting endpoint %s"));
            setExecutor(executorService);
        }
    }

    public void stop() {
        // todo security check
        boolean intr = false;
        try {
            for (Closeable resource : resources) {
                IoUtils.safeClose(resource);
            }
            synchronized (resources) {
                while (! resources.isEmpty()) {
                    try {
                        resources.wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                }
            }
            if (executorService != null) {
                executorService.shutdown();
                boolean done = false;
                do try {
                    done = executorService.awaitTermination(30L, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    intr = true;
                } while (! done);
                executorService = null;
                executor = null;
            }
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Endpoint implementation

    public ConcurrentMap<Object, Object> getAttributes() {
        return endpointMap;
    }

    public <I, O> Handle<RequestHandler> createRequestHandler(final RequestListener<I, O> requestListener, final Class<I> requestClass, final Class<O> replyClass) throws IOException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_REQUEST_HANDLER_PERM);
        }
        LocalRequestHandler.Config<I, O> config = new LocalRequestHandler.Config<I,O>(requestClass, replyClass);
        config.setExecutor(executor);
        config.setRequestListener(requestListener);
        config.setClientContext(new ClientContextImpl(executor));
        final LocalRequestHandler<I, O> localRequestHandler = new LocalRequestHandler<I, O>(config);
        localRequestHandler.addCloseHandler(remover);
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
        if (serviceType == null) {
            throw new NullPointerException("serviceType is null");
        }
        if (groupName == null) {
            throw new NullPointerException("groupName is null");
        }
        if (serviceType.length() == 0) {
            throw new IllegalArgumentException("serviceType is empty");
        }
        if (groupName.length() == 0) {
            throw new IllegalArgumentException("groupName is empty");
        }
        if (metric < 0) {
            throw new IllegalArgumentException("metric must be greater than or equal to zero");
        }
        final LocalRequestHandlerSource.Config<I, O> config = new LocalRequestHandlerSource.Config<I,O>(configuration.getRequestClass(), configuration.getReplyClass());
        config.setRequestListener(configuration.getRequestListener());
        config.setExecutor(executor);
        final LocalRequestHandlerSource<I, O> localRequestHandlerSource = new LocalRequestHandlerSource<I, O>(config);
        final ServiceRegistration registration = new ServiceRegistration(serviceType, groupName, name, localRequestHandlerSource);
        final AbstractSimpleCloseable newHandle = new AbstractSimpleCloseable(executor) {
            protected void closeAction() throws IOException {
                synchronized (serviceLock) {
                    serviceRegistrations.remove(registration);
                }
            }
        };
        registration.setHandle(newHandle);
        synchronized (serviceLock) {
            serviceRegistrations.add(registration);
            for (final ServiceListenerRegistration slr : serviceListenerMap.values()) {
                final ServiceListener listener = slr.getServiceListener();
                try {
                    final ServiceListener.ServiceInfo serviceInfo = new ServiceListener.ServiceInfo();
                    serviceInfo.setEndpointName(name);
                    serviceInfo.setGroupName(groupName);
                    serviceInfo.setServiceType(serviceType);
                    serviceInfo.setMetric(metric);
                    serviceInfo.setRegistrationHandle(newHandle);
                    serviceInfo.setRemote(false);
                    serviceInfo.setRequestHandlerSource(localRequestHandlerSource);
                    listener.serviceRegistered(slr.handle, serviceInfo);
                } catch (Throwable t) {
                    logListenerError(t);
                }
            }
        }
        localRequestHandlerSource.addCloseHandler(remover);
        localRequestHandlerSource.open();
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
            ok = true;
            return clientSource;
        } finally {
            if (! ok) {
                IoUtils.safeClose(handle);
            }
        }
    }

    public <I, O> IoFuture<ClientSource<I, O>> locateService(final URI serviceUri, final Class<I> requestClass, final Class<O> replyClass) throws IllegalArgumentException {
        if (serviceUri == null) {
            throw new NullPointerException("serviceUri is null");
        }
        if (! ServiceURI.isRemotingServiceUri(serviceUri)) {
            throw new IllegalArgumentException("Not a valid remoting service URI");
        }
        final String endpointName = ServiceURI.getEndpointName(serviceUri);
        final String groupName = ServiceURI.getGroupName(serviceUri);
        final String serviceType = ServiceURI.getServiceType(serviceUri);
        synchronized (serviceLock) {
            int bestMetric = Integer.MAX_VALUE;
            List<ServiceRegistration> candidates = CollectionUtil.arrayList();
            for (ServiceRegistration svc : serviceRegistrations) {
                if (svc.matches(serviceType, groupName, endpointName)) {
                    final int metric = svc.getMetric();
                    if (metric < bestMetric) {
                        candidates.clear();
                        candidates.add(svc);
                    } else if (metric == bestMetric) {
                        candidates.add(svc);
                    }
                }
            }
            final int size = candidates.size();
            if (size == 0) {
                final FutureClientSource<I, O> futureClientSource = new FutureClientSource<I, O>();
                final SimpleCloseable listenerHandle = addServiceListener(new ServiceListener() {
                    public void serviceRegistered(final SimpleCloseable listenerHandle, final ServiceInfo info) {
                        final String addedEndpointName = info.getEndpointName();
                        final String addedServiceType = info.getServiceType();
                        final String addedGroupName = info.getGroupName();
                        final RequestHandlerSource requestHandlerSource = info.getRequestHandlerSource();
                        if (endpointName != null && endpointName.length() > 0 && !endpointName.equals(addedEndpointName)) {
                            // no match
                            return;
                        }
                        if (serviceType != null && serviceType.length() > 0 && !serviceType.equals(addedServiceType)) {
                            // no match
                            return;
                        }
                        if (groupName != null && groupName.length() > 0 && !groupName.equals(addedGroupName)) {
                            // no match
                            return;
                        }
                        try {
                            // match!
                            final ClientSource<I, O> clientSource = createClientSource(requestHandlerSource, requestClass, replyClass);
                            futureClientSource.setResult(clientSource);
                        } catch (IOException e) {
                            futureClientSource.setException(e);
                        } finally {
                            IoUtils.safeClose(listenerHandle);
                        }
                    }
                }, true);
                futureClientSource.setListenerHandle(listenerHandle);
                return futureClientSource;
            }
            final RequestHandlerSource handlerSource;
            if (size == 1) {
                handlerSource = candidates.get(0).getHandlerSource();
            } else {
                int idx = (int) ((double) candidates.size() * Math.random());
                handlerSource = candidates.get(idx).getHandlerSource();
            }
            try {
                return new FinishedIoFuture<ClientSource<I,O>>(createClientSource(handlerSource, requestClass, replyClass));
            } catch (IOException e) {
                return new FailedIoFuture<ClientSource<I,O>>(e);
            }
        }
    }

    public SimpleCloseable registerRemoteService(final RemoteServiceConfiguration configuration) throws IllegalArgumentException, IOException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(REGISTER_REMOTE_SERVICE_PERM);
        }
        final RequestHandlerSource handlerSource = configuration.getRequestHandlerSource();
        final String serviceType = configuration.getServiceType();
        final String groupName = configuration.getGroupName();
        final String endpointName = configuration.getEndpointName();
        final int metric = configuration.getMetric();
        if (handlerSource == null) {
            throw new NullPointerException("handlerSource is null");
        }
        if (serviceType == null) {
            throw new NullPointerException("serviceType is null");
        }
        if (groupName == null) {
            throw new NullPointerException("groupName is null");
        }
        if (endpointName == null) {
            throw new NullPointerException("endpointName is null");
        }
        if (serviceType.length() == 0) {
            throw new IllegalArgumentException("serviceType is empty");
        }
        if (groupName.length() == 0) {
            throw new IllegalArgumentException("groupName is empty");
        }
        if (endpointName.length() == 0) {
            throw new IllegalArgumentException("endpointName is empty");
        }
        if (endpointName.equals(name)) {
            throw new IllegalArgumentException("remote endpoint has the same name as the local endpoint");
        }
        if (metric < 1) {
            throw new IllegalArgumentException("metric must be greater than zero");
        }
        final ServiceRegistration registration = new ServiceRegistration(serviceType, groupName, endpointName, metric, handlerSource);
        final AbstractSimpleCloseable newHandle = new AbstractSimpleCloseable(executor) {
            protected void closeAction() throws IOException {
                synchronized (serviceLock) {
                    serviceRegistrations.remove(registration);
                }
            }
        };
        registration.setHandle(newHandle);
        synchronized (serviceLock) {
            serviceRegistrations.add(registration);
            for (final ServiceListenerRegistration slr : serviceListenerMap.values()) {
                final ServiceListener listener = slr.getServiceListener();
                try {
                    final ServiceListener.ServiceInfo info = new ServiceListener.ServiceInfo();
                    info.setEndpointName(endpointName);
                    info.setGroupName(groupName);
                    info.setMetric(metric);
                    info.setRegistrationHandle(newHandle);
                    info.setRemote(true);
                    info.setRequestHandlerSource(handlerSource);
                    info.setServiceType(serviceType);
                    listener.serviceRegistered(slr.handle, info);
                } catch (Throwable t) {
                    logListenerError(t);
                }
            }
        }
        return newHandle;
    }

    public SimpleCloseable addServiceListener(final ServiceListener serviceListener, final boolean onlyNew) {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_SERVICE_LISTENER_PERM);
        }
        final Object key = new Object();
        synchronized (serviceLock) {
            final ServiceListenerRegistration registration = new ServiceListenerRegistration(serviceListener);
            serviceListenerMap.put(key, registration);
            final AbstractSimpleCloseable handle = new AbstractSimpleCloseable(executor) {
                protected void closeAction() throws IOException {
                    synchronized (serviceLock) {
                        serviceListenerMap.remove(key);
                    }
                }
            };
            registration.setHandle(handle);
            if (! onlyNew) {
                for (final ServiceRegistration reg : serviceRegistrations) {
                    try {
                        final ServiceListener.ServiceInfo info = new ServiceListener.ServiceInfo();
                        info.setEndpointName(reg.getEndpointName());
                        info.setGroupName(reg.getGroupName());
                        info.setMetric(reg.getMetric());
                        info.setRegistrationHandle(reg.getHandle());
                        info.setRemote(reg.isRemote());
                        info.setRequestHandlerSource(reg.getHandlerSource());
                        info.setServiceType(reg.getServiceType());
                        serviceListener.serviceRegistered(handle, info);
                    } catch (Throwable t) {
                        logListenerError(t);
                    }
                }
            }
            return handle;
        }
    }

    private static final class ServiceListenerRegistration {
        private final ServiceListener serviceListener;
        private volatile SimpleCloseable handle;

        private ServiceListenerRegistration(final ServiceListener serviceListener) {
            this.serviceListener = serviceListener;
        }

        ServiceListener getServiceListener() {
            return serviceListener;
        }

        void setHandle(final SimpleCloseable handle) {
            this.handle = handle;
        }
    }

    private final ResourceRemover remover = new ResourceRemover();

    private final class ResourceRemover implements CloseHandler<Closeable> {
        public void handleClose(final Closeable closed) {
            synchronized (resources)
            {
                resources.remove(closed);
                if (resources.isEmpty()) {
                    resources.notifyAll();
                }
            }
        }
    }

    public String toString() {
        return "endpoint \"" + name + "\" <" + Integer.toString(hashCode()) + ">";
    }
}
