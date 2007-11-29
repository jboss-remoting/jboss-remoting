package org.jboss.cx.remoting.core.util;

import java.util.Iterator;

/**
 *
 */
public class SynchronizedIterator<T> implements Iterator<T> {
    private final Iterator<T> delegate;
    private final Object monitor;

    protected SynchronizedIterator(final Iterator<T> delegate, final Object monitor) {
        this.delegate = delegate;
        this.monitor = monitor;
    }

    public boolean hasNext() {
        synchronized (monitor) {
            return delegate.hasNext();
        }
    }

    public T next() {
        synchronized (monitor) {
            return delegate.next();
        }
    }

    public void remove() {
        synchronized (monitor) {
            delegate.remove();
        }
    }

    public int hashCode() {
        synchronized (monitor) {
            return delegate.hashCode();
        }
    }

    public boolean equals(Object obj) {
        synchronized (monitor) {
            return delegate.equals(obj);
        }
    }

    public String toString() {
        synchronized (monitor) {
            return delegate.toString();
        }
    }
}
