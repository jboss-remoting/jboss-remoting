/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.stream;

import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.concurrent.Executor;
import org.jboss.marshalling.Pair;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.FutureResult;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoUtils;

/**
 * Handy utility methods for stream types.
 */
public final class Streams {
    private Streams() {
    }

    /**
     * Get an object sink that appends to a collection.
     *
     * @param <T> the collection object type
     * @param target the target collection
     * @return an object sink
     */
    public static <T> ObjectSink<T> getCollectionObjectSink(Collection<T> target) {
        return new CollectionObjectSink<T>(target);
    }

    /**
     * Get an object source that reads from an iterator.
     *
     * @param <T> the iterator object type
     * @param iterator the iterater to read from
     * @return an object source
     */
    public static <T> ObjectSource<T> getIteratorObjectSource(Iterator<T> iterator) {
        return new IteratorObjectSource<T>(iterator);
    }

    /**
     * Get an object source which reads from a collection.
     *
     * @param collection the collection to read from
     * @param <T> the collection member type
     * @return an object source
     */
    public static <T> ObjectSource<T> getCollectionObjectSource(Collection<T> collection) {
        return getIteratorObjectSource(collection.iterator());
    }

    /**
     * Get an object sink that appends to a map.
     *
     * @param target the target map
     * @param <K> the key type
     * @param <V> the value type
     * @return an object sink
     */
    public static <K, V> ObjectSink<Pair<K, V>> getMapObjectSink(Map<K, V> target) {
        return new MapObjectSink<K, V>(target);
    }

    /**
     * Get an object sink that checks the type of each accepted instance.
     *
     * @param delegate the object sink to delegate to
     * @param clazz the class to check for
     * @return a checking object sink
     */
    public static <T> ObjectSink<T> getCheckedObjectSink(final ObjectSink<T> delegate, final Class<? extends T> clazz) {
        return new CheckedObjectSink<T>(delegate, clazz);
    }

    /**
     * Get an object source that reads from an iterator over map entries.
     *
     * @param iterator the iterator object type
     * @param <K> the key type
     * @param <V> the value type
     * @return an object source
     */
    public static <K, V> ObjectSource<Pair<K, V>> getMapEntryIteratorObjectSource(Iterator<Map.Entry<K, V>> iterator) {
        return new MapEntryIteratorObjectSource<K, V>(iterator);
    }

    /**
     * Get an object source that reads from a map.
     *
     * @param map the map
     * @param <K> the key type
     * @param <V> the value type
     * @return an object source
     */
    public static <K, V> ObjectSource<Pair<K, V>> getMapObjectSource(Map<K, V> map) {
        return getMapEntryIteratorObjectSource(map.entrySet().iterator());
    }

    /**
     * Populate a new collection from an object source.  Since the collection may be only partially populated on error,
     * it is recommended that the instance be discarded if an exception is thrown.
     * <p>
     * An example usage which meets this requirement would be: <code><pre>
     * final List&lt;Foo&gt; fooList = getCollection(new ArrayList&lt;Foo&gt;(), fooSource);
     * </pre></code>
     *
     * @param newCollection the new collection to populate
     * @param objectSource the object source to fill the collection from
     * @param <C> the collection type
     * @param <T> the collection value type
     * @return the new collection, populated
     * @throws IOException if an error occurs
     */
    public static <C extends Collection<T>, T> C getCollection(C newCollection, ObjectSource<T> objectSource) throws IOException {
        while (objectSource.hasNext()) {
            newCollection.add(objectSource.next());
        }
        return newCollection;
    }

    /**
     * Populate a new map from an object source.  Since the map may be only partially populated on error,
     * it is recommended that the instance be discarded if an exception is thrown.
     * <p>
     * An example usage which meets this requirement would be: <code><pre>
     * final Map&lt;Foo, Bar&gt; fooBarMap = getMap(new HashMap&lt;Foo, Bar&gt;(), fooBarSource);
     * </pre></code>
     *
     * @param newMap the new map to populate
     * @param objectSource the object source to fill the map from
     * @param <M> the map type
     * @param <K> the map key type
     * @param <V> the map value type
     * @return the new map, populated
     * @throws IOException if an error occurs
     */
    public static <M extends Map<K, V>, K, V> M getMap(M newMap, ObjectSource<Pair<K, V>> objectSource) throws IOException {
        while (objectSource.hasNext()) {
            final Pair<K, V> pair = objectSource.next();
            newMap.put(pair.getA(), pair.getB());
        }
        return newMap;
    }

    /**
     * Populate a new collection from an object source asynchronously.  Since the collection may be only partially populated on error,
     * it is recommended that the instance be discarded if an exception is thrown.
     * <p>
     * An example usage which meets this requirement would be: <code><pre>
     * final IoFuture&lt;? extends List&lt;Foo&gt;&gt; futureFooList = getFutureCollection(executor, new ArrayList&lt;Foo&gt;(), fooSource);
     * </pre></code>
     *
     * @param executor the executor in which to run asynchronous tasks
     * @param newCollection the new collection to populate
     * @param objectSource the object source to fill the collection from
     * @param <C> the collection type
     * @param <T> the collection value type
     * @return the new future collection, populated
     * @throws IOException if an error occurs
     */
    public static <C extends Collection<T>, T> IoFuture<? extends C> getFutureCollection(Executor executor, final C newCollection, final ObjectSource<T> objectSource) {
        final FutureResult<C> futureResult = new FutureResult<C>(executor);
        futureResult.addCancelHandler(new Cancellable() {
            public Cancellable cancel() {
                if (futureResult.setCancelled()) {
                    IoUtils.safeClose(objectSource);
                }
                return this;
            }
        });
        try {
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        while (objectSource.hasNext()) {
                            newCollection.add(objectSource.next());
                        }
                        futureResult.setResult(newCollection);
                    } catch (IOException e) {
                        futureResult.setException(e);
                    }
                }
            });
        } catch (RuntimeException e) {
            final IOException ioe = new IOException("Failed to initiate asynchronous population of a collection");
            ioe.initCause(e);
            futureResult.setException(ioe);
        }
        return futureResult.getIoFuture();
    }

    /**
     * Populate a new map from an object source asynchronously.  Since the map may be only partially populated on error,
     * it is recommended that the instance be discarded if an exception is thrown.
     * <p>
     * An example usage which meets this requirement would be: <code><pre>
     * final IoFuture&lt;? extends Map&lt;Foo, Bar&gt;&gt; futureFooBarMap = getFutureMap(executor, new HashMap&lt;Foo, Bar&gt;(), fooBarSource);
     * </pre></code>
     *
     * @param newMap the new map to populate
     * @param objectSource the object source to fill the map from
     * @param <M> the map type
     * @param <K> the map key type
     * @param <V> the map value type
     * @return the new map, populated
     */
    public static <M extends Map<K, V>, K, V> IoFuture<? extends M> getFutureMap(Executor executor, final M newMap, final ObjectSource<Pair<K, V>> objectSource) {
        final FutureResult<M> futureResult = new FutureResult<M>(executor);
        futureResult.addCancelHandler(new Cancellable() {
            public Cancellable cancel() {
                if (futureResult.setCancelled()) {
                    IoUtils.safeClose(objectSource);
                }
                return this;
            }
        });
        try {
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        while (objectSource.hasNext()) {
                            final Pair<K, V> pair = objectSource.next();
                            newMap.put(pair.getA(), pair.getB());
                        }
                        futureResult.setResult(newMap);
                    } catch (IOException e) {
                        futureResult.setException(e);
                    }
                }
            });
        } catch (RuntimeException e) {
            final IOException ioe = new IOException("Failed to initiate asynchronous population of a collection");
            ioe.initCause(e);
            futureResult.setException(ioe);
        }
        return futureResult.getIoFuture();
    }

    /**
     * Get an object source that reads from an enumeration.
     *
     * @param <T> the enumeration object type
     * @param enumeration the enumeration to read from
     * @return an object source
     */
    public static <T> ObjectSource<T> getEnumerationObjectSource(Enumeration<T> enumeration) {
        return new EnumerationObjectSource<T>(enumeration);
    }

    static Charset getCharset(final String charsetName) throws UnsupportedEncodingException {
        try {
            return Charset.forName(charsetName);
        } catch (UnsupportedCharsetException e) {
            throw new UnsupportedEncodingException(e.getMessage());
        }
    }

    private static final class CollectionObjectSink<T> implements ObjectSink<T> {
        private final Collection<T> target;

        private CollectionObjectSink(final Collection<T> target) {
            this.target = target;
        }

        public void accept(final T instance) throws IOException {
            target.add(instance);
        }

        public void flush() throws IOException {
        }

        public void close() throws IOException {
        }
    }

    private static final class IteratorObjectSource<T> implements ObjectSource<T> {
        private final Iterator<T> src;

        private IteratorObjectSource(final Iterator<T> src) {
            this.src = src;
        }

        public boolean hasNext() throws IOException {
            return src.hasNext();
        }

        public T next() throws IOException {
            try {
                return src.next();
            } catch (NoSuchElementException ex) {
                EOFException eex = new EOFException("Iteration past end of iterator");
                eex.setStackTrace(ex.getStackTrace());
                throw eex;
            }
        }

        public void close() throws IOException {
            //empty
        }
    }

    private static final class EnumerationObjectSource<T> implements ObjectSource<T> {
        private final Enumeration<T> src;

        private EnumerationObjectSource(final Enumeration<T> src) {
            this.src = src;
        }

        public boolean hasNext() throws IOException {
            return src.hasMoreElements();
        }

        public T next() throws IOException {
            try {
                return src.nextElement();
            } catch (NoSuchElementException ex) {
                EOFException eex = new EOFException("Read past end of enumeration");
                eex.setStackTrace(ex.getStackTrace());
                throw eex;
            }
        }

        public void close() throws IOException {
            // empty
        }
    }

    private static final class MapObjectSink<K, V> implements ObjectSink<Pair<K, V>> {

        private final Map<K, V> target;

        private MapObjectSink(final Map<K, V> target) {
            this.target = target;
        }

        public void accept(final Pair<K, V> instance) throws IOException {
            target.put(instance.getA(), instance.getB());
        }

        public void flush() throws IOException {
            // empty
        }

        public void close() throws IOException {
            // empty
        }
    }

    private static final class MapEntryIteratorObjectSource<K, V> implements ObjectSource<Pair<K, V>> {
        private final Iterator<Map.Entry<K, V>> source;

        private MapEntryIteratorObjectSource(final Iterator<Map.Entry<K, V>> source) {
            this.source = source;
        }

        public boolean hasNext() throws IOException {
            return source.hasNext();
        }

        public Pair<K, V> next() throws NoSuchElementException, IOException {
            final Map.Entry<K, V> entry = source.next();
            return Pair.create(entry.getKey(), entry.getValue());
        }

        public void close() throws IOException {
        }
    }

    private static class CheckedObjectSink<T> implements ObjectSink<T> {

        private final ObjectSink<T> delegate;
        private final Class<? extends T> clazz;

        private CheckedObjectSink(final ObjectSink<T> delegate, final Class<? extends T> clazz) {
            this.delegate = delegate;
            this.clazz = clazz;
        }

        public void accept(final T instance) throws IOException {
            delegate.accept(clazz.cast(instance));
        }

        public void flush() throws IOException {
            delegate.flush();
        }

        public void close() throws IOException {
            delegate.close();
        }
    }
}
