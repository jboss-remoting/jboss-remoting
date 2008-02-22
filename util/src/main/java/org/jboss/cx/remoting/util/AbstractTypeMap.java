package org.jboss.cx.remoting.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 *
 */
public abstract class AbstractTypeMap<B> implements TypeMap<B> {
    private final ConcurrentMap<Class<? extends B>, B> map;
    private final Set<Entry<? extends B>> entrySet;

    protected AbstractTypeMap(ConcurrentMap<Class<? extends B>, B> map) {
        if (map == null) {
            throw new NullPointerException("map is null");
        }
        this.map = map;
        entrySet = new EntrySet();
    }

    protected AbstractTypeMap(Map<Class<? extends B>, B> map) {
        if (map == null) {
            throw new NullPointerException("map is null");
        }
        this.map = new AtomicMap<Class<? extends B>, B>(map);
        entrySet = new EntrySet();
    }

    public void clear() {
        map.clear();
    }

    public boolean containsKey(final Class<?> key) {
        return map.containsKey(key);
    }

    public boolean containsValue(final Object value) {
        // since we key by type, we can do an O(1) search for value!
        final Class<? extends Object> claxx = value.getClass();
        return map.containsKey(claxx) && isEqual(value, map.get(claxx));
    }

    private static boolean isEqual(final Object a, final Object b) {
        return (a == null) == (b == null) && (a == null || a.equals(b));
    }

    public Set<Entry<? extends B>> entrySet() {
        return entrySet;
    }

    @SuppressWarnings ({"unchecked"})
    public <T extends B> T get(final Class<T> key) {
        return (T) map.get(key);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Set<Class<? extends B>> keySet() {
        return map.keySet();
    }

    @SuppressWarnings ({"unchecked"})
    public <T extends B> T put(final Class<T> key, final T value) {
        return (T) map.put(key, value);
    }

    public <T extends B> void putAll(final TypeMap<T> m) {
        for (Entry<? extends T> e : m.entrySet()) {
            map.put(e.getKey(), e.getValue());
        }
    }

    @SuppressWarnings ({"unchecked"})
    public <T extends B> T remove(final Class<T> key) {
        return (T) map.remove(key);
    }

    public int size() {
        return map.size();
    }

    public Collection<? extends B> values() {
        return map.values();
    }

    @SuppressWarnings ({"unchecked"})
    public <T extends B> T putIfAbsent(final Class<T> key, final T value) {
        return (T) map.putIfAbsent(key, value);
    }

    public <T extends B> boolean remove(final Class<T> key, final Object value) {
        return map.remove(key, value);
    }

    @SuppressWarnings ({"unchecked"})
    public <T extends B> T replace(final Class<T> key, final T value) {
        return (T) map.replace(key, value);
    }

    public <T extends B> boolean replace(final Class<T> key, final T oldValue, final T newValue) {
        return map.replace(key, oldValue, newValue);
    }

    private final class EntrySet implements Set<Entry<? extends B>> {
        private final Set<Map.Entry<Class<? extends B>,B>> entries = map.entrySet();

        private EntrySet() {
        }

        public int size() {
            return entries.size();
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        public boolean contains(final Object o) {
            // containsValue(T)==true implies containsKey(T.class)==true
            return o instanceof Entry && map.containsValue(((Entry<?>) o).getValue());
        }

        public Iterator<Entry<? extends B>> iterator() {
            return new EntryIterator();
        }

        public Object[] toArray() {
            throw new UnsupportedOperationException("toArray() not allowed");
        }

        public <T> T[] toArray(final T[] a) {
            throw new UnsupportedOperationException("toArray() not allowed");
        }

        public boolean add(final Entry<? extends B> o) {
            throw new UnsupportedOperationException("add() not allowed");
        }

        @SuppressWarnings ({"unchecked"})
        public boolean remove(final Object o) {
            if (! (o instanceof Entry)) {
                return false;
            }
            Class<? extends B> key = ((Entry<? extends B>)o).getKey();
            final Object value = ((Entry<? extends B>) o).getValue();
            return AbstractTypeMap.this.remove(key, value);
        }

        public boolean containsAll(final Collection<?> c) {
            for (Object x : c) {
                if (! contains(x)) {
                    return false;
                }
            }
            return true;
        }

        public boolean addAll(final Collection<? extends Entry<? extends B>> c) {
            throw new UnsupportedOperationException("addAll() not allowed");
        }

        public boolean retainAll(final Collection<?> c) {
            throw new UnsupportedOperationException("retainAll() not allowed");
        }

        public boolean removeAll(final Collection<?> c) {
            throw new UnsupportedOperationException("removeAll() not allowed");
        }

        public void clear() {
            map.clear();
        }
    }

    private final class EntryIterator implements Iterator<Entry<? extends B>> {
        private final Iterator<Map.Entry<Class<? extends B>,B>> iterator = map.entrySet().iterator();

        private EntryIterator() {}

        public boolean hasNext() {
            return iterator.hasNext();
        }

        @SuppressWarnings ({"unchecked"})
        public Entry<? extends B> next() {
            return new EntryImpl(iterator.next());
        }

        public void remove() {
            iterator.remove();
        }
    }

    private final class EntryImpl<Z> implements Entry<Z> {
        private final Map.Entry<Class<Z>, Z> entry;

        public EntryImpl(final Map.Entry<Class<Z>, Z> entry) {
            this.entry = entry;
        }

        public Class<Z> getKey() {
            return entry.getKey();
        }

        public Z getValue() {
            return entry.getValue();
        }

        public Z setValue(final Z value) {
            return entry.setValue(value);
        }

        public boolean equals(Object obj) {
            if (obj instanceof Entry) {
                Entry<?> other = (Entry<?>) obj;
                return isEqual(other.getKey(), entry.getKey()) && isEqual(other.getValue(), entry.getValue());
            } else {
                return false;
            }
        }

        public int hashCode() {
            return entry.hashCode();
        }
    }
}
