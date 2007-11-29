package org.jboss.cx.remoting.spi.filter;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.jboss.cx.remoting.stream.ObjectSink;
import org.jboss.cx.remoting.stream.ObjectSource;

/**
 *
 */
public interface Filter extends ObjectSource<ByteBuffer>, ObjectSink<ByteBuffer> {
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
     * Read the next bufferfull of data from the filter.
     *
     * @return the next object
     *
     * @throws IOException if the stream can no longer be read
     */
    ByteBuffer next() throws IOException;

    /**
     * Receive the next buffer in the stream to be written.
     *
     * @param instance the buffer
     *
     * @throws IOException if an error occurs
     */
    void accept(final ByteBuffer instance) throws IOException;

    /**
     * Flush any unwritten filter data.  May be a no-op on some implementations.
     *
     * @throws IOException if an error occurs
     */
    void flush() throws IOException;

    /**
     * Close the filter.  No more objects may be read from or written to this filter after it has been closed.
     *
     * @throws IOException if an error occurs
     */
    void close() throws IOException;
}
