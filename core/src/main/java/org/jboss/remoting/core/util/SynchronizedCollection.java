package org.jboss.remoting.core.util;

import java.util.Collection;
import java.util.Iterator;

/**
 *
 */
public class SynchronizedCollection<V> implements Collection<V> {
    private final Collection<V> delegate;
    private final Object monitor;

    public SynchronizedCollection(final Collection<V> delegate) {
        this.delegate = delegate;
        monitor = this;
    }

    public SynchronizedCollection(final Collection<V> delegate, final Object monitor) {
        this.delegate = delegate;
        this.monitor = monitor;
    }

    public int size() {
        synchronized (monitor) {
            return delegate.size();
        }
    }

    public boolean isEmpty() {
        synchronized (monitor) {
            return delegate.isEmpty();
        }
    }

    public boolean contains(final Object o) {
        synchronized (monitor) {
            return delegate.contains(o);
        }
    }

    public Iterator<V> iterator() {
        synchronized (monitor) {
            return new SynchronizedIterator<V>(delegate.iterator(), monitor);
        }
    }

    public Object[] toArray() {
        synchronized (monitor) {
            return delegate.toArray();
        }
    }

    public <T> T[] toArray(final T[] a) {
        synchronized (monitor) {
            return delegate.toArray(a);
        }
    }

    public boolean add(final V o) {
        synchronized (monitor) {
            return delegate.add(o);
        }
    }

    public boolean remove(final Object o) {
        synchronized (monitor) {
            return delegate.remove(o);
        }
    }

    public boolean containsAll(final Collection<?> c) {
        synchronized (monitor) {
            return delegate.containsAll(c);
        }
    }

    public boolean addAll(final Collection<? extends V> c) {
        synchronized (monitor) {
            return delegate.addAll(c);
        }
    }

    public boolean removeAll(final Collection<?> c) {
        synchronized (monitor) {
            return delegate.removeAll(c);
        }
    }

    public boolean retainAll(final Collection<?> c) {
        synchronized (monitor) {
            return delegate.retainAll(c);
        }
    }

    public void clear() {
        synchronized (monitor) {
            delegate.clear();
        }
    }

    public boolean equals(final Object o) {
        synchronized (monitor) {
            return delegate.equals(o);
        }
    }

    public int hashCode() {
        synchronized (monitor) {
            return delegate.hashCode();
        }
    }

    public String toString() {
        synchronized (monitor) {
            return delegate.toString();
        }
    }
}
