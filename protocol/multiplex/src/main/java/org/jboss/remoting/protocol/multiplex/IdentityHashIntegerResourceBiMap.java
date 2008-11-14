/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting.protocol.multiplex;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Collections;
import java.util.Collection;
import org.jboss.remoting.spi.AutoCloseable;
import org.jboss.remoting.spi.Handle;

/**
 *
 */
final class IdentityHashIntegerResourceBiMap<T extends AutoCloseable<T>> implements IntegerResourceBiMap<T> {

    private final HashMap<Integer, Handle<T>> leftMap;
    private final IdentityHashMap<T, Integer> rightMap;

    public IdentityHashIntegerResourceBiMap(int initialCapacity, float loadFactor) {
        leftMap = new HashMap<Integer, Handle<T>>(initialCapacity, loadFactor);
        rightMap = new IdentityHashMap<T, Integer>((int) (initialCapacity / loadFactor));
    }

    public IdentityHashIntegerResourceBiMap() {
        this(256, 0.4f);
    }

    public int get(final T key, final int defValue) {
        final Integer v = rightMap.get(key);
        return v == null ? defValue : v.intValue();
    }

    public Handle<T> get(final int key) {
        return leftMap.get(Integer.valueOf(key));
    }

    public void put(final int key1, final Handle<T> key2) {
        final Integer key1Obj = Integer.valueOf(key1);
        final Handle<T> oldKey2 = leftMap.put(key1Obj, key2);
        final Integer oldKey1Obj = rightMap.put(key2.getResource(), key1Obj);
        if (oldKey2 != null) rightMap.remove(oldKey2.getResource());
        if (oldKey1Obj != null) leftMap.remove(oldKey1Obj);
    }

    public Handle<T> remove(final int key) {
        final Handle<T> oldRightKey = leftMap.remove(Integer.valueOf(key));
        if (oldRightKey != null) rightMap.remove(oldRightKey.getResource());
        return oldRightKey;
    }

    public void remove(final T key) {
        leftMap.remove(rightMap.remove(key));
    }

    public Collection<Handle<T>> getKeys() {
        return Collections.unmodifiableCollection(leftMap.values());
    }

    public static <T extends AutoCloseable<T>> IntegerResourceBiMap<T> create() {
        return new IdentityHashIntegerResourceBiMap<T>();
    }

    public static <T extends AutoCloseable<T>> IntegerResourceBiMap<T> createSynchronizing() {
        return Util.synchronizing(new IdentityHashIntegerResourceBiMap<T>());
    }

    public Iterator<Handle<T>> iterator() {
        final Iterator<Map.Entry<Integer, Handle<T>>> delegate = leftMap.entrySet().iterator();
        return new Iterator<Handle<T>>() {
            private Map.Entry<Integer, Handle<T>> current;

            public boolean hasNext() {
                return delegate.hasNext();
            }

            public Handle<T> next() {
                current = delegate.next();
                return current.getValue();
            }

            public void remove() {
                delegate.remove();
                rightMap.remove(current.getValue().getResource());
            }
        };
    }
}