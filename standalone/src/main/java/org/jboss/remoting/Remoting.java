package org.jboss.remoting;

import java.io.IOException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.Collection;
import java.util.Iterator;
import org.jboss.remoting.core.EndpointImpl;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.remoting.spi.Handle;
import org.jboss.xnio.IoUtils;

/**
 *
 */
public final class Remoting {

    public static Endpoint createEndpoint(final String name) {
        return createEndpoint(name, 10);
    }

    public static Endpoint createEndpoint(final String name, final int maxThreads) {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, maxThreads, Long.MAX_VALUE, TimeUnit.NANOSECONDS, new AlwaysBlockingQueue<Runnable>(new SynchronousQueue<Runnable>()), new ThreadPoolExecutor.AbortPolicy());
        final EndpointImpl endpoint = new EndpointImpl(executor, name);
        endpoint.addCloseHandler(new CloseHandler<Endpoint>() {
            public void handleClose(final Endpoint closed) {
                executor.shutdown();
                try {
                    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        return endpoint;
    }

    public static Endpoint createEndpoint(final Executor executor, final String name) {
        return new EndpointImpl(executor, name);
    }

    public static <I, O> Client<I, O> createLocalClient(final Endpoint endpoint, final RequestListener<I, O> requestListener, final Class<I> requestClass, final Class<O> replyClass) throws IOException {
        final Handle<RequestHandler> handle = endpoint.createRequestHandler(requestListener, requestClass, replyClass);
        try {
            return endpoint.createClient(handle.getResource(), requestClass, replyClass);
        } finally {
            IoUtils.safeClose(handle);
        }
    }

    public static <I, O> ClientSource<I, O> createLocalClientSource(final Endpoint endpoint, final LocalServiceConfiguration<I, O> config) throws IOException {
        final Handle<RequestHandlerSource> handle = endpoint.registerService(config);
        try {
            return endpoint.createClientSource(handle.getResource(), config.getRequestClass(), config.getReplyClass());
        } finally {
            IoUtils.safeClose(handle);
        }
    }

    private static class AlwaysBlockingQueue<T> implements BlockingQueue<T> {
        private final BlockingQueue<T> delegate;

        public AlwaysBlockingQueue(final BlockingQueue<T> delegate) {
            this.delegate = delegate;
        }

        public boolean offer(final T o) {
            try {
                delegate.put(o);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        public boolean offer(final T o, final long timeout, final TimeUnit unit) throws InterruptedException {
            return delegate.offer(o, timeout, unit);
        }

        public T poll(final long timeout, final TimeUnit unit) throws InterruptedException {
            return delegate.poll(timeout, unit);
        }

        public T take() throws InterruptedException {
            return delegate.take();
        }

        public void put(final T o) throws InterruptedException {
            delegate.put(o);
        }

        public int remainingCapacity() {
            return delegate.remainingCapacity();
        }

        public boolean add(final T o) {
            return delegate.add(o);
        }

        public int drainTo(final Collection<? super T> c) {
            return delegate.drainTo(c);
        }

        public int drainTo(final Collection<? super T> c, final int maxElements) {
            return delegate.drainTo(c, maxElements);
        }

        public T poll() {
            return delegate.poll();
        }

        public T remove() {
            return delegate.remove();
        }

        public T peek() {
            return delegate.peek();
        }

        public T element() {
            return delegate.element();
        }

        public int size() {
            return delegate.size();
        }

        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        public boolean contains(final Object o) {
            return delegate.contains(o);
        }

        public Iterator<T> iterator() {
            return delegate.iterator();
        }

        public Object[] toArray() {
            return delegate.toArray();
        }

        public <T> T[] toArray(final T[] a) {
            return delegate.toArray(a);
        }

        public boolean remove(final Object o) {
            return delegate.remove(o);
        }

        public boolean containsAll(final Collection<?> c) {
            return delegate.containsAll(c);
        }

        public boolean addAll(final Collection<? extends T> c) {
            return delegate.addAll(c);
        }

        public boolean removeAll(final Collection<?> c) {
            return delegate.removeAll(c);
        }

        public boolean retainAll(final Collection<?> c) {
            return delegate.retainAll(c);
        }

        public void clear() {
            delegate.clear();
        }

        public boolean equals(final Object o) {
            return delegate.equals(o);
        }

        public int hashCode() {
            return delegate.hashCode();
        }
    }

    // privates

    private Remoting() { /* empty */ }
}
