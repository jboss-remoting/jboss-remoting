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

package org.jboss.cx.remoting.util;

import java.util.Set;
import java.util.Collection;

/**
 *
 */
public interface ConcurrentIntegerMap<V> {
    boolean containsKey(int key);

    boolean containsValue(Object value);

    V get(int key);

    V put(int key, V value);

    V putIfAbsent(int key, V value);

    void putAll(ConcurrentIntegerMap<? extends V> m);

    V remove(int key);

    boolean remove(int key, Object oldValue);

    V replace(int key, V value);

    boolean replace(int key, V oldValue, V newValue);

    void clear();

    int size();

    boolean isEmpty();

    Set<Entry<V>> entrySet();

    Collection<V> values();

    boolean equals(Object other);

    int hashCode();

    interface Entry<V> {
        int getKey();

        V getValue();

        V setValue(V value);

        int hashCode();

        boolean equals(Object other);
    }
}
