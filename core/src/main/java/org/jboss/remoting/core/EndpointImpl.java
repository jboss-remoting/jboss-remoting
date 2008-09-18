package org.jboss.remoting.core;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import org.jboss.remoting.Endpoint;
import org.jboss.remoting.RequestListener;
import org.jboss.remoting.CloseHandler;
import org.jboss.remoting.Client;
import org.jboss.remoting.ClientSource;
import org.jboss.remoting.SimpleCloseable;
import org.jboss.remoting.ServiceListener;
import org.jboss.remoting.util.OrderedExecutorFactory;
import org.jboss.remoting.spi.remote.RequestHandler;
import org.jboss.remoting.spi.remote.RequestHandlerSource;
import org.jboss.remoting.spi.remote.Handle;
import org.jboss.remoting.spi.AbstractSimpleCloseable;
import org.jboss.remoting.util.CollectionUtil;
import org.jboss.remoting.util.NamingThreadFactory;
import org.jboss.remoting.util.ServiceURI;
import org.jboss.remoting.version.Version;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.FinishedIoFuture;
import org.jboss.xnio.FailedIoFuture;

/**
 *
 */
public class EndpointImpl implements Endpoint {

    static {
        // Print Remoting "greeting" message
        Logger.getLogger("org.jboss.remoting").info("JBoss Remoting version %s", Version.VERSION);
    }

    private String name;

    private OrderedExecutorFactory orderedExecutorFactory;
    private ExecutorService executorService;

    private final Set<Closeable> resources = CollectionUtil.synchronizedWeakHashSet();
    private final ConcurrentMap<Object, Object> endpointMap = CollectionUtil.concurrentMap();

    private final Object serviceLock = new Object();
    private final Map<Object, ServiceListenerRegistration> serviceListenerMap = CollectionUtil.hashMap();
    private final Set<ServiceRegistration> serviceRegistrations = CollectionUtil.hashSet();

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

    public <I, O> Handle<RequestHandler> createRequestHandler(final RequestListener<I, O> requestListener) throws IOException {
        final LocalRequestHandler<I, O> localRequestHandler = new LocalRequestHandler<I, O>(executor, requestListener);
        localRequestHandler.addCloseHandler(remover);
        localRequestHandler.open();
        return localRequestHandler.getHandle();
    }

    public <I, O> Handle<RequestHandlerSource> createRequestHandlerSource(final RequestListener<I, O> requestListener, final String serviceType, final String groupName) throws IOException {
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
        final LocalRequestHandlerSource<I, O> localRequestHandlerSource = new LocalRequestHandlerSource<I, O>(executor, requestListener);
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
                slr.getExecutor().execute(new Runnable() {
                    public void run() {
                        listener.localServiceCreated(slr.handle, serviceType, groupName, localRequestHandlerSource);
                    }
                });
            }
        }
        localRequestHandlerSource.addCloseHandler(remover);
        localRequestHandlerSource.open();
        return localRequestHandlerSource.getHandle();
    }

    public <I, O> Client<I, O> createClient(final RequestHandler requestHandler) throws IOException {
        boolean ok = false;
        final Handle<RequestHandler> handle = requestHandler.getHandle();
        try {
            final ClientImpl<I, O> client = new ClientImpl<I, O>(handle, executor);
            client.addCloseHandler(new CloseHandler<Client<I, O>>() {
                public void handleClose(final Client<I, O> closed) {
                    IoUtils.safeClose(handle);
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

    public <I, O> ClientSource<I, O> createClientSource(final RequestHandlerSource requestHandlerSource) throws IOException {
        boolean ok = false;
        final Handle<RequestHandlerSource> handle = requestHandlerSource.getHandle();
        try {
            final ClientSourceImpl<I, O> clientSource = new ClientSourceImpl<I, O>(handle, this);
            ok = true;
            return clientSource;
        } finally {
            if (! ok) {
                IoUtils.safeClose(handle);
            }
        }
    }

    public <I, O> IoFuture<ClientSource<I, O>> locateService(final URI serviceUri) throws IllegalArgumentException {
        // todo - should this be typesafe?
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

                    public void localServiceCreated(final SimpleCloseable listenerHandle, final String addedServiceType, final String addedGroupName, final RequestHandlerSource requestHandlerSource) {
                        remoteServiceRegistered(listenerHandle, name, addedServiceType, addedGroupName, 0, requestHandlerSource, null);
                    }

                    public void remoteServiceRegistered(final SimpleCloseable listenerHandle, final String addedEndpointName, final String addedServiceType, final String addedGroupName, final int metric, final RequestHandlerSource requestHandlerSource, final SimpleCloseable handle) {
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
                            final ClientSource<I, O> clientSource = createClientSource(requestHandlerSource);
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
                return new FinishedIoFuture<ClientSource<I,O>>(EndpointImpl.this.<I, O>createClientSource(handlerSource));
            } catch (IOException e) {
                return new FailedIoFuture<ClientSource<I,O>>(e);
            }
        }
    }

    public SimpleCloseable registerRemoteService(final String serviceType, final String groupName, final String endpointName, final RequestHandlerSource handlerSource, final int metric) throws IllegalArgumentException, IOException {
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
                slr.getExecutor().execute(new Runnable() {
                    public void run() {
                        listener.remoteServiceRegistered(slr.handle, endpointName, serviceType, groupName, metric, handlerSource, newHandle);
                    }
                });
            }
        }
        return newHandle;
    }

    public SimpleCloseable addServiceListener(final ServiceListener serviceListener, final boolean onlyNew) {
        final Object key = new Object();
        synchronized (serviceLock) {
            final Executor orderedExecutor = getOrderedExecutor();
            final ServiceListenerRegistration registration = new ServiceListenerRegistration(serviceListener, orderedExecutor);
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
                    if (reg.isRemote()) { // x is remote
                        orderedExecutor.execute(new Runnable() {
                            public void run() {
                                serviceListener.remoteServiceRegistered(handle, reg.getEndpointName(), reg.getServiceType(), reg.getGroupName(), reg.getMetric(), reg.getHandlerSource(), reg.getHandle());
                            }
                        });
                    } else { // x is local
                        orderedExecutor.execute(new Runnable() {
                            public void run() {
                                serviceListener.localServiceCreated(handle, reg.getServiceType(), reg.getGroupName(), reg.getHandlerSource());
                            }
                        });
                    }
                }
            }
            return handle;
        }
    }

    private static final class ServiceListenerRegistration {
        private final ServiceListener serviceListener;
        private final Executor executor;
        private volatile SimpleCloseable handle;

        private ServiceListenerRegistration(final ServiceListener serviceListener, final Executor executor) {
            this.serviceListener = serviceListener;
            this.executor = executor;
        }

        ServiceListener getServiceListener() {
            return serviceListener;
        }

        Executor getExecutor() {
            return executor;
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
