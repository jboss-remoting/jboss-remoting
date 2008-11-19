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

import org.jboss.remoting.core.util.SynchronizedSet;
import java.util.Set;

/**
 *
 */
interface IntegerBiMap<T> {
    int get(T key, int defValue);

    T get(int key);

    void put(int key1, T key2);

    boolean putIfAbsent(int key1, T key2);

    T remove(int key);

    void remove(T key);

    Set<T> getKeys();

    class Util {

        private Util() {
        }

        private static class SyncWrapper<T> implements IntegerBiMap<T> {

            private final IntegerBiMap<T> orig;
            private final Object lock;

            private SyncWrapper(IntegerBiMap<T> orig, Object lock) {
                this.orig = orig;
                this.lock = lock;
            }

            public int get(final T key, final int defValue) {
                synchronized (lock) {
                    return orig.get(key, defValue);
                }
            }

            public T get(final int key) {
                synchronized (lock) {
                    return orig.get(key);
                }
            }

            public void put(final int key1, final T key2) {
                synchronized (lock) {
                    orig.put(key1, key2);
                }
            }

            public boolean putIfAbsent(final int key1, final T key2) {
                synchronized (lock) {
                    return orig.putIfAbsent(key1, key2);
                }
            }

            public T remove(final int key) {
                synchronized (lock) {
                    return orig.remove(key);
                }
            }

            public void remove(final T key) {
                synchronized (lock) {
                    orig.remove(key);
                }
            }

            public Set<T> getKeys() {
                return new SynchronizedSet<T>(orig.getKeys(), lock);
            }
        }

        public static <T> IntegerBiMap<T> synchronizing(IntegerBiMap<T> orig) {
            return new SyncWrapper<T>(orig, new Object());
        }
    }
}
