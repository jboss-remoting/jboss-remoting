package org.jboss.cx.remoting.core.util;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 *
 */
public final class UnmodifiableArrayList<T> extends AbstractList<T> implements List<T> {

    private final Object[] entries;
    private final int offs;
    private final int length;

    <T> UnmodifiableArrayList(final T[] entries) {
        this.entries = entries;
        offs = 0;
        length = entries.length;
    }

    <T> UnmodifiableArrayList(final T[] entries, int offs, int length) {
        if (offs > entries.length) {
            throw new IndexOutOfBoundsException("Specified offset is greater than array length");
        }
        if (offs + length > entries.length) {
            throw new IndexOutOfBoundsException("Specified offset + length is greater than array length");
        }
        this.entries = entries;
        this.offs = offs;
        this.length = length;
    }

    public int size() {
        return length;
    }

    public boolean isEmpty() {
        return length == 0;
    }

    public boolean contains(final Object o) {
        for (int i = 0; i < length; i ++) {
            final Object e = entries[i + offs];
            if (o==null ? e==null : o.equals(e)) {
                return true;
            }
        }
        return false;
    }

    public Iterator<T> iterator() {
        return new ListIteratorImpl<T>();
    }

    @SuppressWarnings ({"unchecked"})
    public T get(final int index) {
        if (index >= length || index < 0) {
            throw new IndexOutOfBoundsException("Invalid get() index: " + index + " (size is " + length + ")");
        }
        return (T) entries[offs + index];
    }

    public ListIterator<T> listIterator() {
        return new ListIteratorImpl<T>();
    }

    public ListIterator<T> listIterator(final int index) {
        return new ListIteratorImpl<T>(index);
    }

    public List<T> subList(final int fromIndex, final int toIndex) {
        if (fromIndex < 0 || fromIndex > length) {
            throw new IndexOutOfBoundsException("fromIndex " + fromIndex + " is not within the bounds of the list (size is " + length + ")");
        }
        if (toIndex < 0 || toIndex > length) {
            throw new IndexOutOfBoundsException("toIndex " + toIndex + " is not within the bounds of the list (size is " + length + ")");
        }
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex " + fromIndex + " is greater than toIndex " + toIndex);
        }
        return new UnmodifiableArrayList<T>(entries, offs + fromIndex, toIndex - fromIndex);
    }

    public final class ListIteratorImpl<T> implements ListIterator<T> {
        private int i;

        private ListIteratorImpl(final int index) {
            i = index;
        }

        private ListIteratorImpl() {
        }

        public boolean hasNext() {
            return i < length;
        }

        @SuppressWarnings ({"unchecked"})
        public T next() {
            if (i < length) {
                return (T) entries[offs + i++];
            } else {
                throw new NoSuchElementException("next() past end of iterator");
            }
        }

        public boolean hasPrevious() {
            return i > 0;
        }

        @SuppressWarnings ({"unchecked"})
        public T previous() {
            if (i > 0) {
                return (T) entries[offs + i--];
            } else {
                throw new NoSuchElementException("next() past end of iterator");
            }
        }

        public int nextIndex() {
            return i;
        }

        public int previousIndex() {
            return i - 1;
        }

        public void remove() {
            throw new UnsupportedOperationException("remove() not allowed");
        }

        public void set(final T o) {
            throw new UnsupportedOperationException("set() not allowed");
        }

        public void add(final T o) {
            throw new UnsupportedOperationException("add() not allowed");
        }
    }
}
