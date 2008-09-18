package org.jboss.remoting.util;

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
