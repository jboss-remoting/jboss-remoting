package org.jboss.cx.remoting.util;

import java.util.Iterator;

/**
 *
 */
public final class DelegateIterable<T> implements Iterable<T> {
    private final Iterable<T> delegate;

    public DelegateIterable(final Iterable<T> delegate) {
        this.delegate = delegate;
    }

    public Iterator<T> iterator() {
        return delegate.iterator();
    }
}
