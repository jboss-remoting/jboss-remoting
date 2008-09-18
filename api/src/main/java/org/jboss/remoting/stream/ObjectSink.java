package org.jboss.remoting.stream;

import java.io.Closeable;
import java.io.IOException;
import java.io.Flushable;

/**
 * A streaming sink for objects.
 *
 * @param <T> the object type
 */
public interface ObjectSink<T> extends Flushable, Closeable {

    /**
     * Accept an object.
     *
     * @param instance the object to accept
     * @throws IOException if an error occurs
     */
    void accept(T instance) throws IOException;

    /**
     * Push out any temporary state.  May be a no-op on some implementations.
     *
     * @throws IOException if an error occurs
     */
    void flush() throws IOException;

    /**
     * Close the sink.
     *
     * @throws IOException if an error occurs
     */
    void close() throws IOException;
}
