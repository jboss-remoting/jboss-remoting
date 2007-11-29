package org.jboss.cx.remoting.spi.stream;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 */
public interface StreamContext extends Closeable {
    /**
     * Send an object to the remote side.
     *
     * @param object the object to send
     *
     * @throws IOException if the send failed
     */
    void sendData(Object object) throws IOException;

    /**
     * Send multple objects to the remote side.
     *
     * @param first the first object to send
     * @param objects the subsequent objects to send
     *
     * @throws IOException if the send failed
     */
    void sendData(Object first, Object... objects) throws IOException;

    /**
     * Wait for a response to come from the remote side.
     *
     * @return the next object, or {@code null} if the end of the stream was reached
     *
     * @throws IOException if an error occurs
     */
    Object receiveData() throws IOException;

    /**
     * Indicate that this stream is exhausted.
     *
     * @throws IOException if the notification did not succeed
     */
    void close() throws IOException;
}
