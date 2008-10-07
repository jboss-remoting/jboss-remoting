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

package org.jboss.remoting.util;

import java.util.concurrent.ConcurrentMap;

/**
 *
 */
public final class EmulatedConcurrentIntegerHashMap<V> implements ConcurrentIntegerMap<V> {

    private final ConcurrentMap<Integer, V> delegate;

    public EmulatedConcurrentIntegerHashMap(final ConcurrentMap<Integer, V> delegate) {
        this.delegate = delegate;
    }

    public V get(final int key) {
        return delegate.get(Integer.valueOf(key));
    }

    public V put(final int key, final V value) {
        return delegate.put(Integer.valueOf(key), value);
    }

    public V putIfAbsent(final int key, final V value) {
        return delegate.putIfAbsent(Integer.valueOf(key), value);
    }

    public V remove(final int key) {
        return delegate.remove(Integer.valueOf(key));
    }

    public boolean remove(final int key, final Object oldValue) {
        return delegate.remove(Integer.valueOf(key), oldValue);
    }

    public V replace(final int key, final V value) {
        return delegate.replace(Integer.valueOf(key), value);
    }

    public boolean replace(final int key, final V oldValue, final V newValue) {
        return delegate.replace(Integer.valueOf(key), oldValue, newValue);
    }

    public void clear() {
        delegate.clear();
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public boolean equals(final Object obj) {
        return super.equals(obj);
    }

    public int hashCode() {
        return super.hashCode();
    }
}
