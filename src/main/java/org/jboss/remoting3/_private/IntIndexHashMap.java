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

package org.jboss.remoting3._private;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Lock-free concurrent integer-indexed hash map.
 *
 * @param <V> the value type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class IntIndexHashMap<V> extends AbstractCollection<V> implements IntIndexMap<V> {
    private static final int DEFAULT_INITIAL_CAPACITY = 512;
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final float DEFAULT_LOAD_FACTOR = 0.60f;

    /** A row which has been resized into the new view. */
    private static final Object[] RESIZED = new Object[0];
    /** A non-existent table entry (as opposed to a {@code null} value). */
    private static final Object NONEXISTENT = new Object();

    private final IntIndexer<? super V> indexer;
    private final Equaller<? super V> ve;

    private volatile Table<V> table;

    private final float loadFactor;
    private final int initialCapacity;

    @SuppressWarnings("unchecked")
    private static final AtomicIntegerFieldUpdater<Table> sizeUpdater = AtomicIntegerFieldUpdater.newUpdater(Table.class, "size");

    @SuppressWarnings("unchecked")
    private static final AtomicReferenceFieldUpdater<IntIndexHashMap, Table> tableUpdater = AtomicReferenceFieldUpdater.newUpdater(IntIndexHashMap.class, Table.class, "table");

    /**
     * Construct a new instance.
     *
     * @param indexer the key indexer
     * @param valueEqualler the value equaller
     * @param initialCapacity the initial capacity
     * @param loadFactor the load factor
     */
    public IntIndexHashMap(IntIndexer<? super V> indexer, Equaller<? super V> valueEqualler, int initialCapacity, float loadFactor) {
        if (valueEqualler == null) {
            throw new IllegalArgumentException("valueEqualler is null");
        }
        this.indexer = indexer;
        ve = valueEqualler;
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity must be > 0");
        }
        if (initialCapacity > MAXIMUM_CAPACITY) {
            initialCapacity = MAXIMUM_CAPACITY;
        }
        if (loadFactor <= 0.0 || Float.isNaN(loadFactor) || loadFactor >= 1.0) {
            throw new IllegalArgumentException("Load factor must be between 0.0f and 1.0f");
        }

        int capacity = 1;

        while (capacity < initialCapacity) {
            capacity <<= 1;
        }

        this.loadFactor = loadFactor;
        this.initialCapacity = capacity;

        final Table<V> table = new Table<V>(capacity, loadFactor);
        tableUpdater.set(this, table);
    }

    /**
     * Construct a new instance.
     *
     * @param indexer the key indexer
     * @param valueEqualler the value equaller
     */
    public IntIndexHashMap(IntIndexer<? super V> indexer, Equaller<? super V> valueEqualler) {
        this(indexer, valueEqualler, DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Construct a new instance.
     *
     * @param indexer the key indexer
     * @param initialCapacity the initial capacity
     * @param loadFactor the load factor
     */
    public IntIndexHashMap(IntIndexer<? super V> indexer, int initialCapacity, final float loadFactor) {
        this(indexer, Equaller.DEFAULT, initialCapacity, loadFactor);
    }

    /**
     * Construct a new instance.
     *
     * @param indexer the key indexer
     * @param loadFactor the load factor
     */
    public IntIndexHashMap(IntIndexer<? super V> indexer, final float loadFactor) {
        this(indexer, DEFAULT_INITIAL_CAPACITY, loadFactor);
    }

    /**
     * Construct a new instance.
     *
     * @param indexer the key indexer
     * @param initialCapacity the initial capacity
     */
    public IntIndexHashMap(IntIndexer<? super V> indexer, final int initialCapacity) {
        this(indexer, initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Construct a new instance.
     *
     * @param indexer the key indexer
     */
    public IntIndexHashMap(IntIndexer<? super V> indexer) {
        this(indexer, DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    public V putIfAbsent(final V value) {
        final V result = doPut(value, true, table);
        return result == NONEXISTENT ? null : result;
    }

    public V removeKey(final int index) {
        final V result = doRemove(index, table);
        return result == NONEXISTENT ? null : result;
    }

    @SuppressWarnings({ "unchecked" })
    public boolean remove(final Object value) {
        return doRemove((V) value, table);
    }

    public boolean containsKey(final int index) {
        return doGet(table, index) != NONEXISTENT;
    }

    public V get(final int index) {
        final V result = doGet(table, index);
        return result == NONEXISTENT ? null : result;
    }

    public V put(final V value) {
        final V result = doPut(value, false, table);
        return result == NONEXISTENT ? null : result;
    }

    public V replace(final V value) {
        final V result = doReplace(value, table);
        return result == NONEXISTENT ? null : result;
    }

    public boolean replace(final V oldValue, final V newValue) {
        if (indexer.getKey(oldValue) != indexer.getKey(newValue)) {
            throw new IllegalArgumentException("Can only replace with value which has the same key");
        }
        return doReplace(oldValue, newValue, table);
    }

    public int getKey(final V argument) {
        return indexer.getKey(argument);
    }

    public boolean add(final V v) {
        return doPut(v, true, table) == NONEXISTENT;
    }

    public <T> T[] toArray(final T[] a) {
        final ArrayList<T> list = new ArrayList<T>(size());
        list.addAll((Collection<T>) this);
        return list.toArray(a);
    }

    public Object[] toArray() {
        final ArrayList<Object> list = new ArrayList<Object>(size());
        list.addAll(this);
        return list.toArray();
    }

    @SuppressWarnings({ "unchecked" })
    public boolean contains(final Object o) {
        return ve.equals((V) o, get(indexer.getKey((V) o)));
    }

    public Iterator<V> iterator() {
        return new EntryIterator();
    }

    public int size() {
        return table.size & 0x7fffffff;
    }

    private boolean doReplace(final V oldValue, final V newValue, final Table<V> table) {
        final int key = indexer.getKey(oldValue);
        final AtomicReferenceArray<V[]> array = table.array;
        final int idx = key & array.length() - 1;

        OUTER: for (;;) {
            // Fetch the table row.
            V[] oldRow = array.get(idx);
            if (oldRow == null) {
                // no match for the key
                return false;
            }
            if (oldRow == RESIZED) {
                return doReplace(oldValue, newValue, table.resizeView);
            }

            for (int i = 0, length = oldRow.length; i < length; i++) {
                final V tryItem = oldRow[i];
                if (ve.equals(tryItem, oldValue)) {
                    final V[] newRow = oldRow.clone();
                    newRow[i] = newValue;
                    if (array.compareAndSet(i, oldRow, newRow)) {
                        return true;
                    } else {
                        continue OUTER;
                    }
                }
            }
            return false;
        }
    }

    private V doReplace(final V value, final Table<V> table) {
        final int key = indexer.getKey(value);
        final AtomicReferenceArray<V[]> array = table.array;
        final int idx = key & array.length() - 1;

        OUTER: for (;;) {
            // Fetch the table row.
            V[] oldRow = array.get(idx);
            if (oldRow == null) {
                // no match for the key
                return nonexistent();
            }
            if (oldRow == RESIZED) {
                return doReplace(value, table.resizeView);
            }

            // Find the matching Item in the row.
            for (int i = 0, length = oldRow.length; i < length; i++) {
                final V tryItem = oldRow[i];
                if (key == indexer.getKey(tryItem)) {
                    final V[] newRow = oldRow.clone();
                    newRow[i] = value;
                    if (array.compareAndSet(i, oldRow, newRow)) {
                        return tryItem;
                    } else {
                        continue OUTER;
                    }
                }
            }
            return nonexistent();
        }
    }

    private boolean doRemove(final V item, final Table<V> table) {
        int key = indexer.getKey(item);

        final AtomicReferenceArray<V[]> array = table.array;
        final int idx = key & array.length() - 1;

        V[] oldRow;

        OUTER: for (;;) {
            oldRow = array.get(idx);
            if (oldRow == null) {
                return false;
            }
            if (oldRow == RESIZED) {
                boolean result;
                if (result = doRemove(item, table.resizeView)) {
                    sizeUpdater.getAndDecrement(table);
                }
                return result;
            }

            for (int i = 0; i < oldRow.length; i ++) {
                if (ve.equals(item, oldRow[i])) {
                    if (array.compareAndSet(idx, oldRow, remove(oldRow, i))) {
                        sizeUpdater.getAndDecrement(table);
                        return true;
                    } else {
                        continue OUTER;
                    }
                }
            }
            // not found
            return false;
        }
    }

    private V doRemove(final int key, final Table<V> table) {
        final AtomicReferenceArray<V[]> array = table.array;
        final int idx = key & array.length() - 1;

        V[] oldRow;

        OUTER: for (;;) {
            oldRow = array.get(idx);
            if (oldRow == null) {
                return nonexistent();
            }
            if (oldRow == RESIZED) {
                V result;
                if ((result = doRemove(key, table.resizeView)) != NONEXISTENT) {
                    sizeUpdater.getAndDecrement(table);
                }
                return result;
            }

            for (int i = 0; i < oldRow.length; i ++) {
                if (key == indexer.getKey(oldRow[i])) {
                    if (array.compareAndSet(idx, oldRow, remove(oldRow, i))) {
                        sizeUpdater.getAndDecrement(table);
                        return oldRow[i];
                    } else {
                        continue OUTER;
                    }
                }
            }
            // not found
            return nonexistent();
        }
    }

    private V doPut(V value, boolean ifAbsent, Table<V> table) {
        final int hashCode = indexer.getKey(value);
        final AtomicReferenceArray<V[]> array = table.array;
        final int idx = hashCode & array.length() - 1;

        OUTER: for (;;) {

            // Fetch the table row.
            V[] oldRow = array.get(idx);
            if (oldRow == RESIZED) {
                // row was transported to the new table so recalculate everything
                final V result = doPut(value, ifAbsent, table.resizeView);
                // keep a consistent size view though!
                if (result == NONEXISTENT) sizeUpdater.getAndIncrement(table);
                return result;
            }
            if (oldRow != null) {
                // Find the matching Item in the row.
                V oldItem;
                for (int i = 0, length = oldRow.length; i < length; i++) {
                    if (hashCode == indexer.getKey(oldRow[i])) {
                        if (ifAbsent) {
                            return oldRow[i];
                        } else {
                            V[] newRow = oldRow.clone();
                            newRow[i] = value;
                            oldItem = oldRow[i];
                            if (array.compareAndSet(idx, oldRow, newRow)) {
                                return oldItem;
                            } else {
                                // retry
                                continue OUTER;
                            }
                        }
                    }
                }
            }

            if (array.compareAndSet(idx, oldRow, addItem(oldRow, value))) {
                // Up the table size.
                final int threshold = table.threshold;
                int newSize = sizeUpdater.incrementAndGet(table);
                // if the sign bit is set the value will be < 0 meaning if a resize is in progress this condition is false
                while (newSize > threshold) {
                    if (sizeUpdater.compareAndSet(table, newSize, newSize | 0x80000000)) {
                        resize(table);
                        break;
                    } else {
                        newSize = table.size;
                    }
                }
                // Success.
                return nonexistent();
            }
        }
    }

    private void resize(Table<V> origTable) {
        final AtomicReferenceArray<V[]> origArray = origTable.array;
        final int origCapacity = origArray.length();
        final Table<V> newTable = new Table<V>(origCapacity << 1, loadFactor);
        // Prevent resize until we're done...
        newTable.size = 0x80000000;
        origTable.resizeView = newTable;
        final AtomicReferenceArray<V[]> newArray = newTable.array;

        for (int i = 0; i < origCapacity; i ++) {
            // for each row, try to resize into two new rows
            V[] origRow, newRow0, newRow1;
            int count0 = 0, count1 = 0;
            do {
                origRow = origArray.get(i);
                if (origRow != null) {
                    for (V item : origRow) {
                        if ((indexer.getKey(item) & origCapacity) == 0) {
                            count0++;
                        } else {
                            count1++;
                        }
                    }
                    if (count0 != 0) {
                        newRow0 = createRow(count0);
                        int j = 0;
                        for (V item : origRow) {
                            if ((indexer.getKey(item) & origCapacity) == 0) {
                                newRow0[j++] = item;
                            }
                        }
                        newArray.lazySet(i, newRow0);
                    }
                    if (count1 != 0) {
                        newRow1 = createRow(count1);
                        int j = 0;
                        for (V item : origRow) {
                            if ((indexer.getKey(item) & origCapacity) != 0) {
                                newRow1[j++] = item;
                            }
                        }
                        newArray.lazySet(i + origCapacity, newRow1);
                    }
                }
            } while (! origArray.compareAndSet(i, origRow, IntIndexHashMap.<V>resized()));
            sizeUpdater.getAndAdd(newTable, count0 + count1);
        }

        int size;
        do {
            size = newTable.size;
            if ((size & 0x7fffffff) >= newTable.threshold) {
                // shorter path for reads and writes
                table = newTable;
                // then time for another resize, right away
                resize(newTable);
                return;
            }
        } while (!sizeUpdater.compareAndSet(newTable, size, size & 0x7fffffff));

        // All done, plug in the new table
        table = newTable;
    }

    private static <V> V[] remove(V[] row, int idx) {
        final int len = row.length;
        assert idx < len;
        if (len == 1) {
            return null;
        }
        V[] newRow = createRow(len - 1);
        if (idx > 0) {
            System.arraycopy(row, 0, newRow, 0, idx);
        }
        if (idx < len - 1) {
            System.arraycopy(row, idx + 1, newRow, idx, len - 1 - idx);
        }
        return newRow;
    }

    private V doGet(final Table<V> table, final int key) {
        final AtomicReferenceArray<V[]> array = table.array;
        final V[] row = array.get(key & (array.length() - 1));
        if (row != null) for (V item : row) {
            if (key == indexer.getKey(item)) {
                return item;
            }
        }
        return nonexistent();
    }

    public void clear() {
        table = new Table<V>(initialCapacity, loadFactor);
    }

    private static <V> V[] addItem(final V[] row, final V newItem) {
        if (row == null) {
            return createRow(newItem);
        } else {
            final int length = row.length;
            V[] newRow = Arrays.copyOf(row, length + 1);
            newRow[length] = newItem;
            return newRow;
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> V[] createRow(final V newItem) {
        return (V[]) new Object[] { newItem };
    }

    @SuppressWarnings("unchecked")
    private static <V> V[] createRow(final int length) {
        return (V[]) new Object[length];
    }

    @SuppressWarnings("unchecked")
    private static <V> V nonexistent() {
        return (V) NONEXISTENT;
    }

    @SuppressWarnings("unchecked")
    private static <V> V[] resized() {
        return (V[]) RESIZED;
    }

    final class RowIterator implements Iterator<V> {
        private final Table<V> table;
        V[] row;

        private int idx;
        private int removeIdx = -1;
        private V next = nonexistent();

        RowIterator(final Table<V> table, final V[] row) {
            this.table = table;
            this.row = row;
        }

        public boolean hasNext() {
            while (next == NONEXISTENT) {
                final V[] row = this.row;
                if (row == null || idx == row.length) {
                    return false;
                }
                next = row[idx++];
            }
            return true;
        }

        public V next() {
            if (hasNext()) try {
                removeIdx = idx - 1;
                return next;
            } finally {
                next = nonexistent();
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            int removeIdx = this.removeIdx;
            this.removeIdx = -1;
            if (removeIdx == -1) {
                throw new IllegalStateException("next() not yet called");
            }
            doRemove(row[removeIdx], table);
        }
    }

    final class BranchIterator implements Iterator<V> {
        private final Iterator<V> branch0;
        private final Iterator<V> branch1;

        private boolean branch;

        BranchIterator(final Iterator<V> branch0, final Iterator<V> branch1) {
            this.branch0 = branch0;
            this.branch1 = branch1;
        }

        public boolean hasNext() {
            return branch0.hasNext() || branch1.hasNext();
        }

        public V next() {
            if (branch) {
                return branch1.next();
            }
            if (branch0.hasNext()) {
                return branch0.next();
            } else {
                branch = true;
                return branch1.next();
            }
        }

        public void remove() {
            if (branch) {
                branch0.remove();
            } else {
                branch1.remove();
            }
        }
    }

    private Iterator<V> createRowIterator(Table<V> table, int rowIdx) {
        final AtomicReferenceArray<V[]> array = table.array;
        final V[] row = array.get(rowIdx);
        if (row == RESIZED) {
            final Table<V> resizeView = table.resizeView;
            return new BranchIterator(createRowIterator(resizeView, rowIdx), createRowIterator(resizeView, rowIdx + array.length()));
        } else {
            return new RowIterator(table, row);
        }
    }

    final class EntryIterator implements Iterator<V> {
        private final Table<V> table = IntIndexHashMap.this.table;
        private Iterator<V> tableIterator;
        private Iterator<V> removeIterator;
        private int tableIdx;
        private V next;

        public boolean hasNext() {
            while (next == null) {
                if (tableIdx == table.array.length()) {
                    return false;
                }
                if (tableIterator == null) {
                    tableIterator = createRowIterator(table, tableIdx++);
                }
                if (tableIterator.hasNext()) {
                    next = tableIterator.next();
                    return true;
                } else {
                    tableIterator = null;
                }
            }
            return true;
        }

        public V next() {
            if (hasNext()) try {
                return next;
            } finally {
                removeIterator = tableIterator;
                next = null;
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            final Iterator<V> removeIterator = this.removeIterator;
            if (removeIterator == null) {
                throw new IllegalStateException();
            } else try {
                removeIterator.remove();
            } finally {
                this.removeIterator = null;
            }
        }
    }

    static final class Table<V> {
        final AtomicReferenceArray<V[]> array;
        final int threshold;
        /** Bits 0-30 are size; bit 31 is 1 if the table is being resized. */
        volatile int size;
        volatile Table<V> resizeView;

        private Table(int capacity, float loadFactor) {
            array = new AtomicReferenceArray<V[]>(capacity);
            threshold = capacity == MAXIMUM_CAPACITY ? Integer.MAX_VALUE : (int)(capacity * loadFactor);
        }
    }
}
