package org.jboss.cx.remoting.stream;

import java.io.Closeable;
import java.io.IOException;

/**
 *
 */
public interface ObjectSink<T> extends Closeable {
    void accept(T instance) throws IOException;

    /**
     * Push out any temporary state.  May be a no-op on some implementations.
     *
     * @throws IOException if an error occurs
     */
    void flush() throws IOException;

    void close() throws IOException;
}
