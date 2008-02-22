package org.jboss.cx.remoting.util;

import java.io.Closeable;
import java.io.IOException;

/**
 * A readable source of byte data.
 */
public interface ByteInput extends Closeable {
    /**
     * Read one byte.
     *
     * @return the byte, or -1 if the end of the stream has been reached.
     * @throws IOException if an I/O error occurs
     */
    int read() throws IOException;

    /**
     * Read a series of bytes into an array.
     *
     * @param data the array into which data is to be read
     * @return the total number of bytes read, or -1 if there are no bytes remaining to read
     * @throws IOException if an I/O error occurs
     */
    int read(byte[] data) throws IOException;

    /**
     * Read a series of bytes into an array.
     *
     * @param data the array into which data is to be read
     * @param offs the start offset in the {@code data} array at which the data is written
     * @param len the maximum number of bytes to read
     * @return the total number of bytes read, or -1 if there are no bytes remaining to read
     * @throws IOException if an I/O error occurs
     */
    int read(byte[] data, int offs, int len) throws IOException;

    /**
     * Return the number of bytes remaining.
     *
     * @return the number of bytes, or -1 if the byte count cannot be determined
     */
    int remaining();
}
