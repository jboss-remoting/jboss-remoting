package org.jboss.cx.remoting.core.util;

import java.util.Set;

/**
 *
 */
public class SynchronizedSet<K> extends SynchronizedCollection<K> implements Set<K> {

    public SynchronizedSet(final Set<K> delegate) {
        super(delegate);
    }

    protected SynchronizedSet(final Set<K> delegate, final Object monitor) {
        super(delegate, monitor);
    }
}
