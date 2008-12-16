package org.jboss.remoting;

import java.io.IOException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Executors;
import org.jboss.remoting.core.EndpointImpl;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.remoting.spi.Handle;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.CloseableExecutor;
import org.jboss.xnio.log.Logger;

/**
 * The standalone interface into Remoting.  This class contains static methods that are useful to standalone programs
 * for managing endpoints and services in a simple fashion.
 */
public final class Remoting {

    private static final Logger log = Logger.getLogger("org.jboss.remoting");

    /**
     * Create an endpoint.  The endpoint will create its own thread pool with a maximum of 10 threads.
     *
     * @param name the name of the endpoint
     * @return the endpoint
     */
    public static Endpoint createEndpoint(final String name) {
        return createEndpoint(name, 10);
    }

    /**
     * Create an endpoint.  The endpoint will create its own thread pool with a maximum of {@code maxThreads} threads.
     *
     * @param name the name of the endpoint
     * @param maxThreads the maximum thread count
     * @return the endpoint
     */
    public static Endpoint createEndpoint(final String name, final int maxThreads) {
        final CloseableExecutor executor = createExecutor(maxThreads);
        final EndpointImpl endpoint = new EndpointImpl(executor, name);
        endpoint.addCloseHandler(new CloseHandler<Endpoint>() {
            public void handleClose(final Endpoint closed) {
                IoUtils.safeClose(executor);
            }
        });
        return endpoint;
    }

    private static final ThreadFactory OUR_THREAD_FACTORY = new ThreadFactory() {
        private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();

        public Thread newThread(final Runnable r) {
            final Thread thread = defaultThreadFactory.newThread(r);
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(final Thread t, final Throwable e) {
                    log.error(e, "Uncaught exception in thread %s", t);
                }
            });
            return thread;
        }
    };

    /**
     * Create a simple thread pool that is compatible with Remoting.  The thread pool will have a maximum of {@code maxThreads}
     * threads.
     *
     * @param maxThreads the maximum thread count
     * @return a closeable executor
     */
    public static CloseableExecutor createExecutor(final int maxThreads) {
        return IoUtils.closeableExecutor(new ThreadPoolExecutor(1, maxThreads, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(50), OUR_THREAD_FACTORY, new ThreadPoolExecutor.CallerRunsPolicy()), 30L, TimeUnit.SECONDS);
    }

    /**
     * Create an endpoint using the given {@code Executor} to execute tasks.
     *
     * @param executor the executor to use
     * @param name the name of the endpoint
     * @return the endpoint
     */
    public static Endpoint createEndpoint(final Executor executor, final String name) {
        return new EndpointImpl(executor, name);
    }

    /**
     * Create a local client from a request listener.  The client will retain the sole reference to the request listener,
     * so when the client is closed, the listener will also be closed (unless the client is sent to a remote endpoint).
     *
     * @param endpoint the endpoint to bind the request listener to
     * @param requestListener the request listener
     * @param requestClass the request class
     * @param replyClass the reply class
     * @param <I> the request type
     * @param <O> the reply type
     * @return a new client
     * @throws IOException if an error occurs
     */
    public static <I, O> Client<I, O> createLocalClient(final Endpoint endpoint, final RequestListener<I, O> requestListener, final Class<I> requestClass, final Class<O> replyClass) throws IOException {
        final Handle<RequestHandler> handle = endpoint.createRequestHandler(requestListener, requestClass, replyClass);
        try {
            return endpoint.createClient(handle.getResource(), requestClass, replyClass);
        } finally {
            IoUtils.safeClose(handle);
        }
    }

    /**
     * Create a local client source from a local service configuration.  The client source will be registered on the endpoint.
     *
     * @param endpoint the endpoint to bind the service to
     * @param config the service configuration
     * @param <I> the request type
     * @param <O> the reply type
     * @return a new client source
     * @throws IOException if an error occurs
     */
    public static <I, O> ClientSource<I, O> createLocalClientSource(final Endpoint endpoint, final LocalServiceConfiguration<I, O> config) throws IOException {
        final Handle<RequestHandlerSource> handle = endpoint.registerService(config);
        try {
            return endpoint.createClientSource(handle.getResource(), config.getRequestClass(), config.getReplyClass());
        } finally {
            IoUtils.safeClose(handle);
        }
    }

    private Remoting() { /* empty */ }
}
