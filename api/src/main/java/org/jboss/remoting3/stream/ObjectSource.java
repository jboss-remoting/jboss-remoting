package org.jboss.remoting3.stream;

import java.io.Closeable;
import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * A streaming source for objects.
 *
 * @param <T> the object type
 */
public interface ObjectSource<T> extends Closeable {

    /**
     * Indicate whether there are more objects to retrieve.  If this method returns {@code true}, an object is
     * guaranteed to be available.  If this method returns {@code false}, the end of stream has been reached.
     * <p/>
     * If this method returns {@code true}, it will continue to return {@code true} on every subsequent invocation until
     * the next object is pulled using the {@code next()} method, or until the object source is closed.  This method
     * may block until the presence of the next object in the stream has been ascertained.
     *
     * @return {@code true} if there are more objects in this stream
     */
    boolean hasNext() throws IOException;

    /**
     * Get the next object in the stream.  The {@code hasNext()} method should be called before this method is called
     * to avoid receiving a {@code NoSuchElementException}.
     *
     * @return the next object
     *
     * @throws NoSuchElementException if no object is available
     * @throws IOException if an I/O error occurs
     */
    T next() throws NoSuchElementException, IOException;

    /**
     * Close the stream.  No more objects may be read from this stream after it has been closed.
     *
     * @throws IOException if an I/O error occurs
     */
    void close() throws IOException;
}
