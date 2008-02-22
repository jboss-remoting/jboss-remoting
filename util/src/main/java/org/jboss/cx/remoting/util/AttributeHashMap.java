package org.jboss.cx.remoting.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 *
 */
public final class AttributeHashMap implements AttributeMap {
    private final ConcurrentMap<AttributeKey<?>, Object> map = CollectionUtil.concurrentMap();

    @SuppressWarnings ({"unchecked"})
    public <T> T get(AttributeKey<T> key) {
        return (T) map.get(key);
    }

    @SuppressWarnings ({"unchecked"})
    public <T> T put(AttributeKey<T> key, T value) {
        return (T) map.put(key, value);
    }

    @SuppressWarnings ({"unchecked"})
    public <T> T remove(AttributeKey<T> key) {
        return (T) map.remove(key);
    }

    public <T> boolean remove(AttributeKey<T> key, T value) {
        return map.remove(key, value);
    }

    @SuppressWarnings ({"unchecked"})
    public <T> T putIfAbsent(AttributeKey<T> key, T value) {
        return (T) map.putIfAbsent(key, value);
    }

    public <T> boolean replace(AttributeKey<T> key, T oldValue, T newValue) {
        return map.replace(key, oldValue, newValue);
    }

    public <T> boolean containsKey(AttributeKey<T> key) {
        return map.containsKey(key);
    }

    public <T> boolean containsValue(T value) {
        return map.containsValue(value);
    }

    public Iterable<Entry<?>> entries() {
        return new Iterable<Entry<?>>() {
            public Iterator<Entry<?>> iterator() {
                final Iterator<Map.Entry<AttributeKey<?>, Object>> i = map.entrySet().iterator();
                return new Iterator<Entry<?>>() {
                    public boolean hasNext() {
                        return i.hasNext();
                    }

                    public Entry<?> next() {
                        final Map.Entry<AttributeKey<?>, Object> ie = i.next();
                        return new Entry<Object>() {
                            @SuppressWarnings ({"unchecked"})
                            public AttributeKey<Object> getKey() {
                                return (AttributeKey<Object>) ie.getKey();
                            }

                            public Object getValue() {
                                return ie.getValue();
                            }

                            public void setValue(final Object newValue) {
                                ie.setValue(newValue);
                            }
                        };
                    }

                    public void remove() {
                        i.remove();
                    }
                };
            }
        };
    }

    public Set<AttributeKey<?>> keySet() {
        return map.keySet();
    }

    public Collection<?> values() {
        return map.values();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }
}
