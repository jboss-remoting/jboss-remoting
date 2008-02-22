package org.jboss.cx.remoting.util;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * A writable destination for byte data.
 */
public interface ByteOutput extends Closeable, Flushable {
    /**
     * Write a single byte of data.  The input argument is truncated to 8 bits.
     *
     * @param b the byte to write
     * @throws IOException if an I/O error occurs
     */
    void write(int b) throws IOException;

    /**
     * Write many bytes of data.
     *
     * @param b the bytes to write
     * @throws IOException if an I/O error occurs
     */
    void write(byte[] b) throws IOException;

    /**
     * Write many bytes of data.
     *
     * @param b the bytes to write
     * @param offs the offset in {@code b} to start reading bytes from
     * @param len the number of bytes to write
     * @throws IOException if an I/O error occurs
     */
    void write(byte[] b, int offs, int len) throws IOException;

    /**
     * Commit the written data.  This causes the accumulated data to be sent as a message on the underlying
     * channel.
     *
     * @throws IOException if an I/O error occurs
     */
    void commit() throws IOException;

    /**
     * Get a count of the number of bytes written to this message.
     *
     * @return the count
     * @throws IOException if an I/O error occurs
     */
    int getBytesWritten() throws IOException;
}
