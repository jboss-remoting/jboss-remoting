/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.jboss.remoting3.remote;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class UnlockedReadIntIndexHashMap<V> {

    private static final int DEFAULT_INITIAL_CAPACITY = 512;
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final float DEFAULT_LOAD_FACTOR = 0.60f;

    // Final fields (thread-safe)
    private final Object writeLock = new Object();
    private final float loadFactor;
    private final IntIndexer<? super V> indexer;

    // Volatile fields (writes protected by {@link #writeLock})
    private volatile int size;
    private volatile AtomicReferenceArray<V[]> table;

    // Raw fields (reads and writes protected by {@link #writeLock}
    private int threshold;

    public UnlockedReadIntIndexHashMap(int initialCapacity, final float loadFactor, final IntIndexer<? super V> indexer) {
        this.indexer = indexer;
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity must be > 0");
        }
        if (initialCapacity > MAXIMUM_CAPACITY) {
            initialCapacity = MAXIMUM_CAPACITY;
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException("Load factor must be > 0.0f");
        }

        int capacity = 1;

        while (capacity < initialCapacity) {
            capacity <<= 1;
        }

        this.loadFactor = loadFactor;
        synchronized (writeLock) {
            threshold = (int)(capacity * loadFactor);
            table = new AtomicReferenceArray<V[]>(capacity);
        }
    }

    public UnlockedReadIntIndexHashMap(final float loadFactor, final IntIndexer<? super V> indexer) {
        this(DEFAULT_INITIAL_CAPACITY, loadFactor, indexer);
    }

    public UnlockedReadIntIndexHashMap(final int initialCapacity, final IntIndexer<? super V> indexer) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR, indexer);
    }

    public UnlockedReadIntIndexHashMap(final IntIndexer<? super V> indexer) {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, indexer);
    }

    private void resize() {
        assert Thread.holdsLock(writeLock);
        final AtomicReferenceArray<V[]> oldTable = table;
        final int oldCapacity = oldTable.length();
        if (oldCapacity == MAXIMUM_CAPACITY) {
            return;
        }
        final int newCapacity = oldCapacity << 1;
        final AtomicReferenceArray<V[]> newTable = new AtomicReferenceArray<V[]>(newCapacity);
        final int newThreshold = (int)(newCapacity * loadFactor);
        for (int i = 0; i < oldCapacity; i ++) {
            final V[] items = oldTable.get(i);
            if (items != null) {
                final IntIndexer<? super V> indexer = this.indexer;
                final int length = items.length;
                for (int j = 0; j < length; j++) {
                    V item = items[j];
                    final int hc = indexer.indexOf(item) & (newCapacity - 1);
                    final V[] old = newTable.get(hc);
                    if (old == null) {
                        @SuppressWarnings("unchecked")
                        final V[] newRow = (V[]) new Object[] { item };
                        newTable.lazySet(hc, newRow);
                    } else {
                        final int oldLen = old.length;
                        final V[] copy = Arrays.copyOf(old, oldLen + 1);
                        copy[oldLen] = item;
                        newTable.lazySet(hc, copy);
                    }
                }
            }
        }
        table = newTable;
        threshold = newThreshold;
    }

    private V doPut(AtomicReferenceArray<V[]> table, int key, V value, boolean ifAbsent) {
        assert Thread.holdsLock(writeLock);
        final int hc = key & table.length() - 1;
        final V[] old = table.get(hc);
        if (old == null) {
            @SuppressWarnings("unchecked")
            final V[] newRow = (V[]) new Object[] { value };
            table.set(hc, newRow);
            if (size++ == threshold) {
                resize();
            }
            return null;
        } else {
            final int oldLen = old.length;
            V existing;
            final IntIndexer<? super V> indexer = this.indexer;
            for (int i = 0; i < oldLen; i++) {
                existing = old[i];
                if (indexer.equals(existing, key)) {
                    try {
                        return existing;
                    } finally {
                        if (! ifAbsent) {
                            // replace
                            final V[] newRow = old.clone();
                            newRow[i] = value;
                            table.set(hc, newRow);
                        }
                    }
                }
            }
            final V[] newRow = Arrays.copyOf(old, oldLen + 1);
            newRow[oldLen] = value;
            table.set(hc, newRow);
            if (size++ == threshold) {
                resize();
            }
            return null;
        }
    }

    private static <V> V[] remove(V[] row, int idx) {
        final int len = row.length;
        assert idx < len;
        if (len == 1) {
            return null;
        }
        final int lenMinusOne = len - 1;
        if (idx == lenMinusOne) {
            return Arrays.copyOf(row, idx);
        }
        @SuppressWarnings("unchecked")
        V[] newRow = (V[]) new Object[lenMinusOne];
        if (idx > 0) {
            System.arraycopy(row, 0, newRow, 0, idx);
        }
        if (idx < lenMinusOne) {
            System.arraycopy(row, idx + 1, newRow, idx, lenMinusOne - idx);
        }
        return newRow;
    }

    public int size() {
        return size;
    }

    public boolean containsKey(final int key) {
        final AtomicReferenceArray<V[]> table = this.table;
        final int hc = key & table.length() - 1;
        final V[] row = table.get(hc);
        if (row != null) {
            final IntIndexer<? super V> indexer = this.indexer;
            final int rowLen = row.length;
            for (int i = 0; i < rowLen; i ++) {
                if (indexer.equals(row[i], key)) {
                    return true;
                }
            }
        }
        return false;
    }

    public V get(final int key) {
        final AtomicReferenceArray<V[]> table = this.table;
        final int hc = key & table.length() - 1;
        final V[] row = table.get(hc);
        if (row != null) {
            final IntIndexer<? super V> indexer = this.indexer;
            final int rowLen = row.length;
            V value;
            for (int i = 0; i < rowLen; i ++) {
                value = row[i];
                if (indexer.equals(value, key)) {
                    return value;
                }
            }
        }
        return null;
    }

    public V put(final V value) {
        synchronized (writeLock) {
            return doPut(table, indexer.indexOf(value), value, false);
        }
    }

    public V remove(final int key) {
        synchronized (writeLock) {
            final AtomicReferenceArray<V[]> table = this.table;
            final int hc = key & table.length() - 1;
            final V[] row = table.get(hc);
            if (row == null) {
                return null;
            }
            final IntIndexer<? super V> indexer = this.indexer;
            final int rowLen = row.length;
            for (int i = 0; i < rowLen; i++) {
                final V item = row[i];
                if (indexer.equals(item, key)) {
                    table.set(hc, remove(row, i));
                    size --;
                    return item;
                }
            }
            return null;
        }
    }

    public void clear() {
        synchronized (writeLock) {
            table = new AtomicReferenceArray<V[]>(table.length());
            size = 0;
        }
    }

    public V putIfAbsent(final int key, final V value) {
        synchronized (writeLock) {
            return doPut(table, key, value, true);
        }
    }

    public boolean remove(final int key, final V value) {
        synchronized (writeLock) {
            final AtomicReferenceArray<V[]> table = this.table;
            final int hc = key & table.length() - 1;
            final V[] row = table.get(hc);
            if (row == null) {
                return false;
            }
            final IntIndexer<? super V> indexer = this.indexer;
            final int rowLen = row.length;
            for (int i = 0; i < rowLen; i++) {
                final V item = row[i];
                if (indexer.equals(item, key) && (value == null ? item == null : value.equals(item))) {
                    table.set(hc, remove(row, i));
                    size --;
                    return true;
                }
            }
            return false;
        }
    }

    public boolean replace(final int key, final V oldValue, final V newValue) {
        synchronized (writeLock) {
            final AtomicReferenceArray<V[]> table = this.table;
            final int hc = key & table.length() - 1;
            final V[] row = table.get(hc);
            if (row == null) {
                return false;
            }
            final int rowLen = row.length;
            V existing;
            final IntIndexer<? super V> indexer = this.indexer;
            for (int i = 0; i < rowLen; i++) {
                existing = row[i];
                if (indexer.equals(existing, key)) {
                    if (existing == null ? oldValue == null : existing.equals(oldValue)) {
                        final V[] newRow = row.clone();
                        newRow[i] = newValue;
                        table.set(hc, newRow);
                        return true;
                    } else {
                        return false;
                    }
                }
            }
            return false;
        }
    }

    public V replace(final int key, final V value) {
        synchronized (writeLock) {
            final AtomicReferenceArray<V[]> table = this.table;
            final int hc = key & table.length() - 1;
            final V[] row = table.get(hc);
            if (row == null) {
                return null;
            }
            final int rowLen = row.length;
            V existing;
            final IntIndexer<? super V> indexer = this.indexer;
            for (int i = 0; i < rowLen; i++) {
                existing = row[i];
                if (indexer.equals(existing, key)) {
                    final V[] newRow = row.clone();
                    final V oldValue = newRow[i];
                    newRow[i] = value;
                    table.set(hc, newRow);
                    return oldValue;
                }
            }
            return null;
        }
    }
}
