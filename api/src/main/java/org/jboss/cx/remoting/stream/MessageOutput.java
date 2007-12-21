package org.jboss.cx.remoting.stream;

import java.io.Closeable;
import java.io.Flushable;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectOutput;

/**
 *
 */
public interface MessageOutput extends Closeable, Flushable, DataOutput, ObjectOutput {
    /**
     * Terminate this message and release any underlying resources.  This method does NOT call {@code flush}.
     *
     * @throws IOException if the close failed
     */
    void close() throws IOException;

    /**
     * Cause the message, as written thus far, to be sent.
     *
     * @throws IOException if the message could not be sent
     */
    void flush() throws IOException;

    // TODO - maybe for later...
    // MessageOutput writeSlice(int size) throws IOException;
}
