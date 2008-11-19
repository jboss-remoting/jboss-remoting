package org.jboss.remoting.core.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * A delegating map implementation that accepts a standard {@code Map}, but conforms to the contract
 * for {@code ConcurrentMap}.  No synchronization is done on the delegate.
 */
public class AtomicMap<K, V> implements ConcurrentMap<K, V> {
    private final Map<K, V> delegate;

    public AtomicMap(final Map<K, V> delegate) {
        this.delegate = delegate;
    }

    public int size() {
        return delegate.size();
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public boolean containsKey(final Object key) {
        return delegate.containsKey(key);
    }

    public boolean containsValue(final Object value) {
        return delegate.containsValue(value);
    }

    public V get(final Object key) {
        return delegate.get(key);
    }

    public V put(final K key, final V value) {
        return delegate.put(key, value);
    }

    public V remove(final Object key) {
        return delegate.remove(key);
    }

    public void putAll(final Map<? extends K, ? extends V> t) {
        delegate.putAll(t);
    }

    public void clear() {
        delegate.clear();
    }

    public Set<K> keySet() {
        return delegate.keySet();
    }

    public Collection<V> values() {
        return delegate.values();
    }

    public Set<Entry<K, V>> entrySet() {
        return delegate.entrySet();
    }

    public boolean equals(final Object o) {
        return delegate.equals(o);
    }

    public int hashCode() {
        return delegate.hashCode();
    }

    public V putIfAbsent(final K key, final V value) {
        if (delegate.containsKey(key)) {
            return delegate.get(key);
        } else {
            return delegate.put(key, value);
        }
    }

    public boolean remove(final Object key, final Object value) {
        if (delegate.containsKey(key) && (value == null && delegate.get(key) == null || delegate.get(key).equals(value))) {
            delegate.remove(key);
            return true;
        } else {
            return false;
        }
    }

    public boolean replace(final K key, final V oldValue, final V newValue) {
        if (delegate.containsKey(key) && (oldValue == null ? delegate.get(key) == null : delegate.get(key).equals(oldValue))) {
            delegate.put(key, newValue);
            return true;
        } else {
            return false;
        }
    }

    public V replace(final K key, final V value) {
        if (delegate.containsKey(key)) {
            return delegate.put(key, value);
        } else {
            return null;
        }
    }
}
