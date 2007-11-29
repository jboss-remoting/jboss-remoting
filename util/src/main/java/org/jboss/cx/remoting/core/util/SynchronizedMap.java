package org.jboss.cx.remoting.core.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 *
 */
public class SynchronizedMap<K, V> implements ConcurrentMap<K, V> {
    private final Object monitor;
    private final Map<K, V> delegate;

    public SynchronizedMap(final Map<K, V> delegate) {
        this.delegate = delegate;
        monitor = this;
    }

    protected SynchronizedMap(final Map<K, V> delegate, final Object monitor) {
        this.monitor = monitor;
        this.delegate = delegate;
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

    public boolean containsKey(final Object key) {
        synchronized (monitor) {
            return delegate.containsKey(key);
        }
    }

    public boolean containsValue(final Object value) {
        synchronized (monitor) {
            return delegate.containsValue(value);
        }
    }

    public V get(final Object key) {
        synchronized (monitor) {
            return delegate.get(key);
        }
    }

    public V put(final K key, final V value) {
        synchronized (monitor) {
            return delegate.put(key, value);
        }
    }

    public V remove(final Object key) {
        synchronized (monitor) {
            return delegate.remove(key);
        }
    }

    public void putAll(final Map<? extends K, ? extends V> t) {
        synchronized (monitor) {
            delegate.putAll(t);
        }
    }

    public void clear() {
        synchronized (monitor) {
            delegate.clear();
        }
    }

    public Set<K> keySet() {
        synchronized (monitor) {
            return new SynchronizedSet<K>(delegate.keySet(), monitor);
        }
    }

    public Collection<V> values() {
        synchronized (monitor) {
            return new SynchronizedCollection<V>(delegate.values(), monitor);
        }
    }

    public Set<Entry<K, V>> entrySet() {
        synchronized (monitor) {
            return new SynchronizedSet<Entry<K, V>>(delegate.entrySet(), monitor);
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

    public V putIfAbsent(final K key, final V value) {
        synchronized (monitor) {
            if (delegate.containsKey(key)) {
                return delegate.get(key);
            } else {
                return delegate.put(key, value);
            }
        }
    }

    public boolean remove(final Object key, final Object value) {
        synchronized (monitor) {
            if (delegate.containsKey(key) && (value == null && delegate.get(key) == null || delegate.get(key).equals(value))) {
                delegate.remove(key);
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean replace(final K key, final V oldValue, final V newValue) {
        synchronized (monitor) {
            if (delegate.containsKey(key) && (oldValue == null ? delegate.get(key) == null : delegate.get(key).equals(oldValue))) {
                delegate.put(key, newValue);
                return true;
            } else {
                return false;
            }
        }
    }

    public V replace(final K key, final V value) {
        synchronized (monitor) {
            if (delegate.containsKey(key)) {
                return delegate.put(key, value);
            } else {
                return null;
            }
        }
    }

    public String toString() {
        synchronized (monitor) {
            return delegate.toString();
        }
    }
}
