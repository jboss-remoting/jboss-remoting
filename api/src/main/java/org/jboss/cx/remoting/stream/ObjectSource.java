package org.jboss.cx.remoting.stream;

import java.io.Closeable;
import java.io.IOException;

/**
 * A streaming source for objects.
 *
 * @param <T> the object type
 */
public interface ObjectSource<T> extends Closeable {

    /**
     * Indicate whether there are more objects to retrieve.  If this method returns {@code true}, an object is
     * guaranteed to be available.  If this method returns {@code false}, there is no object available; however, an
     * object may become available at a later time, depending on the implementation.
     * <p/>
     * If this method returns {@code true}, it will continue to return {@code true} on every subsequent invocation until
     * the next object is pulled using the {@code next()} method, or until the object source is closed.
     *
     * @return {@code true} if there are more objects in this stream
     */
    boolean hasNext() throws IOException;

    /**
     * Get the next object in the stream.
     *
     * @return the next object
     *
     * @throws IOException if the stream can no longer be read
     */
    T next() throws IOException;

    /**
     * Close the stream.  No more objects may be read from this stream after it has been closed.
     *
     * @throws IOException if an error occurs
     */
    void close() throws IOException;
}
