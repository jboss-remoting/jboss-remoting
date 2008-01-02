package org.jboss.cx.remoting.core.util;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 *
 */
public final class CollectionUtil {
    private CollectionUtil() {
    }

    public static <K, V> ConcurrentMap<K, V> concurrentMap() {
        if (true) {
            return concurrentMap(new HashMap<K,V>());
        } else {
            return new ConcurrentHashMap<K, V>();
        }
    }

    public static <K, V> ConcurrentMap<K, V> concurrentMap(Map<K, V> original) {
        return new SynchronizedMap<K, V>(original);
    }

    public static <T> List<T> arrayList() {
        return new ArrayList<T>();
    }

    public static <T> List<T> arrayList(List<T> orig) {
        return new ArrayList<T>(orig);
    }

    public static <T> Set<T> synchronizedSet(Set<T> nested) {
        return new SynchronizedSet<T>(nested);
    }

    public static <T> BlockingQueue<T> synchronizedQueue(Queue<T> nested) {
        return new SynchronizedQueue<T>(nested);
    }

    public static <T> Set<T> weakHashSet() {
        return new WeakHashSet<T>();
    }

    public static <T> BlockingQueue<T> blockingQueue(int size) {
        return new ArrayBlockingQueue<T>(size);
    }

    public static <T> Iterable<T> protectedIterable(Iterable<T> original) {
        return new DelegateIterable<T>(original);
    }

    public static <T> Set<T> hashSet() {
        return new HashSet<T>();
    }

    public static <T> Iterable<T> loop(final Enumeration<T> enumeration) {
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                return new Iterator<T>() {
                    public boolean hasNext() {
                        return enumeration.hasMoreElements();
                    }

                    public T next() {
                        return enumeration.nextElement();
                    }

                    public void remove() {
                        throw new UnsupportedOperationException("remove() not supported");
                    }
                };
            }
        };
    }

    public static <T> Iterable<T> loop(final Iterator<T> iterator) {
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                return iterator;
            }
        };
    }

    public static Iterable<String> split(final String delimiter, final String subject) {
        return new Iterable<String>() {
            public Iterator<String> iterator() {
                return new Iterator<String>(){
                    private int position = 0;

                    public boolean hasNext() {
                        return position != -1;
                    }

                    public String next() {
                        if (position == -1) {
                            throw new NoSuchElementException("next() past end of iterator");
                        }
                        final int nextDelim = subject.indexOf(delimiter, position);
                        try {
                            if (nextDelim == -1) {
                                return subject.substring(position);
                            } else {
                                return subject.substring(position, nextDelim);
                            }
                        } finally {
                            position = nextDelim;
                        }
                    }

                    public void remove() {
                        throw new UnsupportedOperationException("remove() not supported");
                    }
                };
            }
        };
    }

    public static <K, V> Map<K, V> weakHashMap() {
        return new WeakHashMap<K, V>();
    }


    public static <K, V> ConcurrentMap<K, V> concurrentWeakHashMap() {
        return CollectionUtil.<K,V>concurrentMap(CollectionUtil.<K,V>weakHashMap());
    }

    public static <T> List<T> unmodifiableList(final T[] entries) {
        return new UnmodifiableArrayList<T>(entries);
    }

    public static <K, V> Map<K, V> hashMap() {
        return new HashMap<K, V>();
    }
}
