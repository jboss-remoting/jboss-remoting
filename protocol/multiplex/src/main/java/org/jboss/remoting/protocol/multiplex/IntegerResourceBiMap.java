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

import org.jboss.remoting.spi.AutoCloseable;
import org.jboss.remoting.spi.Handle;
import org.jboss.remoting.core.util.SynchronizedCollection;
import java.util.Iterator;
import java.util.Collection;

/**
 *
 */
interface IntegerResourceBiMap<T extends AutoCloseable<T>> extends Iterable<Handle<T>> {
    int get(T key, int defValue);

    Handle<T> get(int key);

    void put(int key1, Handle<T> key2);

    Handle<T> remove(int key);

    void remove(T key);

    Collection<Handle<T>> getKeys();

    class Util {

        private Util() {
        }

        private static class SyncWrapper<T extends AutoCloseable<T>> implements IntegerResourceBiMap<T> {

            private final IntegerResourceBiMap<T> orig;
            private final Object lock;

            private SyncWrapper(IntegerResourceBiMap<T> orig, Object lock) {
                this.orig = orig;
                this.lock = lock;
            }

            public int get(final T key, final int defValue) {
                synchronized (lock) {
                    return orig.get(key, defValue);
                }
            }

            public Handle<T> get(final int key) {
                synchronized (lock) {
                    return orig.get(key);
                }
            }

            public void put(final int key1, final Handle<T> key2) {
                synchronized (lock) {
                    orig.put(key1, key2);
                }
            }

            public Handle<T> remove(final int key) {
                synchronized (lock) {
                    return orig.remove(key);
                }
            }

            public void remove(final T key) {
                synchronized (lock) {
                    orig.remove(key);
                }
            }

            public Collection<Handle<T>> getKeys() {
                return new SynchronizedCollection<Handle<T>>(orig.getKeys(), lock);
            }

            public Iterator<Handle<T>> iterator() {
                return null;
            }
        }

        public static <T extends AutoCloseable<T>> IntegerResourceBiMap<T> synchronizing(IntegerResourceBiMap<T> orig) {
            return new SyncWrapper<T>(orig, new Object());
        }
    }
}
