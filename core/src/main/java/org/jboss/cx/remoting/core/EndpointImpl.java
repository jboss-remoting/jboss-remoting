package org.jboss.cx.remoting.core;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.io.Closeable;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.ClientSource;
import org.jboss.cx.remoting.core.util.OrderedExecutorFactory;
import org.jboss.cx.remoting.spi.remote.RemoteClientEndpoint;
import org.jboss.cx.remoting.spi.remote.RemoteServiceEndpoint;
import org.jboss.cx.remoting.spi.remote.Handle;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.util.NamingThreadFactory;
import org.jboss.cx.remoting.version.Version;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.IoUtils;

/**
 *
 */
public class EndpointImpl implements Endpoint {

    static {
        // Print Remoting "greeting" message
        Logger.getLogger("org.jboss.cx.remoting").info("JBoss Remoting version %s", Version.VERSION);
    }

    private String name;

    private OrderedExecutorFactory orderedExecutorFactory;
    private ExecutorService executorService;

    private final Set<Closeable> resources = CollectionUtil.synchronizedWeakHashSet();
    private final ConcurrentMap<Object, Object> endpointMap = CollectionUtil.concurrentMap();

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

    public <I, O> Handle<RemoteClientEndpoint> createClientEndpoint(final RequestListener<I, O> requestListener) throws RemotingException {
        final RemoteClientEndpointLocalImpl<I, O> clientEndpoint = new RemoteClientEndpointLocalImpl<I, O>(executor, requestListener);
        clientEndpoint.addCloseHandler(remover);
        clientEndpoint.open();
        return clientEndpoint.getHandle();
    }

    public <I, O> Handle<RemoteServiceEndpoint> createServiceEndpoint(final RequestListener<I, O> requestListener) throws RemotingException {
        final RemoteServiceEndpointLocalImpl<I, O> serviceEndpoint = new RemoteServiceEndpointLocalImpl<I, O>(executor, requestListener);
        serviceEndpoint.addCloseHandler(remover);
        serviceEndpoint.open();
        return serviceEndpoint.getHandle();
    }

    public <I, O> Client<I, O> createClient(final RemoteClientEndpoint endpoint) throws RemotingException {
        boolean ok = false;
        final Handle<RemoteClientEndpoint> handle = endpoint.getHandle();
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

    public <I, O> ClientSource<I, O> createClientSource(final RemoteServiceEndpoint remoteServiceEndpoint) throws RemotingException {
        boolean ok = false;
        final Handle<RemoteServiceEndpoint> handle = remoteServiceEndpoint.getHandle();
        try {
            final ClientSourceImpl<I, O> client = new ClientSourceImpl<I, O>(handle, this);
            ok = true;
            return client;
        } finally {
            if (! ok) {
                IoUtils.safeClose(handle);
            }
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
