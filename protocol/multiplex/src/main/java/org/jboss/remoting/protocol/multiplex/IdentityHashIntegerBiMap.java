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

/**
 *
 */
final class IdentityHashIntegerBiMap<T> implements IntegerBiMap<T> {

    private final HashMap<Integer, T> leftMap;
    private final IdentityHashMap<T, Integer> rightMap;

    public IdentityHashIntegerBiMap(int initialCapacity, float loadFactor) {
        leftMap = new HashMap<Integer, T>(initialCapacity, loadFactor);
        rightMap = new IdentityHashMap<T, Integer>((int) (initialCapacity / loadFactor));
    }

    public IdentityHashIntegerBiMap() {
        this(256, 0.4f);
    }

    public int get(final T key, final int defValue) {
        final Integer v = rightMap.get(key);
        return v == null ? defValue : v.intValue();
    }

    public T get(final int key) {
        return leftMap.get(Integer.valueOf(key));
    }

    public void put(final int key1, final T key2) {
        final Integer key1Obj = Integer.valueOf(key1);
        final T oldKey2 = leftMap.put(key1Obj, key2);
        final Integer oldKey1Obj = rightMap.put(key2, key1Obj);
        rightMap.remove(oldKey2);
        leftMap.remove(oldKey1Obj);
    }

    public T remove(final int key) {
        final T oldRightKey = leftMap.remove(Integer.valueOf(key));
        rightMap.remove(oldRightKey);
        return oldRightKey;
    }

    public void remove(final T key) {
        leftMap.remove(rightMap.remove(key));
    }

    public static <T> IntegerBiMap<T> create() {
        return new IdentityHashIntegerBiMap<T>();
    }

    public static <T> IntegerBiMap<T> createSynchronizing() {
        return IntegerBiMap.Util.synchronizing(new IdentityHashIntegerBiMap<T>());
    }
}
