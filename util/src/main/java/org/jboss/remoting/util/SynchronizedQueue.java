package org.jboss.remoting.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class SynchronizedQueue<T> implements BlockingQueue<T> {
    private final Queue<T> delegate;
    private final Object monitor;

    public SynchronizedQueue(final Queue<T> delegate) {
        this.delegate = delegate;
        monitor = this;
    }

    protected SynchronizedQueue(final Queue<T> delegate, final Object monitor) {
        this.monitor = monitor;
        this.delegate = delegate;
    }

    public boolean offer(final T o) {
        synchronized(monitor) {
            return delegate.offer(o);
        }
    }

    public T poll() {
        synchronized(monitor) {
            return delegate.poll();
        }
    }

    public T remove() {
        synchronized(monitor) {
            return delegate.remove();
        }
    }

    public T peek() {
        synchronized(monitor) {
            return delegate.peek();
        }
    }

    public T element() {
        synchronized(monitor) {
            return delegate.element();
        }
    }

    public int size() {
        synchronized(monitor) {
            return delegate.size();
        }
    }

    public boolean isEmpty() {
        synchronized(monitor) {
            return delegate.isEmpty();
        }
    }

    public boolean contains(final Object o) {
        synchronized(monitor) {
            return delegate.contains(o);
        }
    }

    public Iterator<T> iterator() {
        synchronized(monitor) {
            return delegate.iterator();
        }
    }

    public Object[] toArray() {
        synchronized(monitor) {
            return delegate.toArray();
        }
    }

    public <T> T[] toArray(final T[] a) {
        synchronized(monitor) {
            //noinspection SuspiciousToArrayCall
            return delegate.toArray(a);
        }
    }

    public boolean add(final T o) {
        synchronized(monitor) {
            return delegate.add(o);
        }
    }

    public boolean remove(final Object o) {
        synchronized(monitor) {
            return delegate.remove(o);
        }
    }

    public boolean containsAll(final Collection<?> c) {
        synchronized(monitor) {
            return delegate.containsAll(c);
        }
    }

    public boolean addAll(final Collection<? extends T> c) {
        synchronized(monitor) {
            return delegate.addAll(c);
        }
    }

    public boolean removeAll(final Collection<?> c) {
        synchronized(monitor) {
            return delegate.removeAll(c);
        }
    }

    public boolean retainAll(final Collection<?> c) {
        synchronized(monitor) {
            return delegate.retainAll(c);
        }
    }

    public void clear() {
        synchronized(monitor) {
            delegate.clear();
        }
    }

    public boolean equals(final Object o) {
        synchronized(monitor) {
            return delegate.equals(o);
        }
    }

    public int hashCode() {
        synchronized(monitor) {
            return delegate.hashCode();
        }
    }

    public boolean offer(final T o, final long timeout, final TimeUnit unit) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = unit.toMillis(timeout);
        long deadline = startTime + timeoutMillis < 0L ? Long.MAX_VALUE : startTime + timeoutMillis;
        synchronized(monitor) {
            for (;;) {
                if (offer(o)) {
                    return true;
                }
                if (deadline <= startTime) {
                    return false;
                }
                monitor.wait(deadline - startTime);
                startTime = System.currentTimeMillis();
            }
        }
    }

    public T poll(final long timeout, final TimeUnit unit) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = unit.toMillis(timeout);
        long deadline = startTime + timeoutMillis < 0L ? Long.MAX_VALUE : startTime + timeoutMillis;
        synchronized(monitor) {
            for (;;) {
                final T v = poll();
                if (v != null) {
                    return v;
                }
                if (deadline <= startTime) {
                    return null;
                }
                monitor.wait(deadline - startTime);
                startTime = System.currentTimeMillis();
            }
        }
    }

    public T take() throws InterruptedException {
        synchronized(monitor) {
            for (;;) {
                final T v = poll();
                if (v != null) {
                    return v;
                }
                monitor.wait();
            }
        }
    }

    public void put(final T o) throws InterruptedException {
        synchronized(monitor) {
            for(;;) {
                if (add(o)) {
                    return;
                }
                monitor.wait();
            }
        }
    }

    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    public int drainTo(final Collection<? super T> c) {
        if (c == this) {
            throw new IllegalArgumentException("Attempt to drain queue to itself");
        }
        int cnt = 0;
        synchronized(monitor) {
            for (;;) {
                T v = poll();
                if (v == null) {
                    return cnt;
                } else {
                    c.add(v);
                    cnt++;
                }
            }
        }
    }

    public int drainTo(final Collection<? super T> c, final int maxElements) {
        if (c == this) {
            throw new IllegalArgumentException("Attempt to drain queue to itself");
        }
        int cnt = 0;
        synchronized(monitor) {
            for (;;) {
                T v = poll();
                if (v == null) {
                    return cnt;
                } else {
                    c.add(v);
                    cnt++;
                    if (cnt == maxElements) {
                        return cnt;
                    }
                }
            }
        }
    }
}
