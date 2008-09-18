package org.jboss.remoting.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

/**
 *
 */
public final class WeakHashSet<T> implements Set<T> {
    private final WeakHashMap<T,Void> map = new WeakHashMap<T,Void>();

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean contains(final Object o) {
        return map.containsKey(o);
    }

    public Iterator<T> iterator() {
        return map.keySet().iterator();
    }

    public Object[] toArray() {
        return map.keySet().toArray();
    }

    public <T> T[] toArray(final T[] a) {
        return map.keySet().toArray(a);
    }

    public boolean add(final T o) {
        try {
            return ! map.containsKey(o);
        } finally {
            map.put(o, null);
        }
    }

    public boolean remove(final Object o) {
        return map.keySet().remove(o);
    }

    public boolean containsAll(final Collection<?> c) {
        return map.keySet().containsAll(c);
    }

    public boolean addAll(final Collection<? extends T> c) {
        boolean changed = false;
        for (T t : c) {
            if (! map.containsKey(t)) {
                changed = true;
                map.put(t, null);
            }
        }
        return changed;
    }

    public boolean retainAll(final Collection<?> c) {
        return map.keySet().retainAll(c);
    }

    public boolean removeAll(final Collection<?> c) {
        return map.keySet().removeAll(c);
    }

    public void clear() {
        map.clear();
    }
}
