package org.jboss.cx.remoting.spi.stream;

import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectOutput;
import org.jboss.cx.remoting.stream.MessageOutput;

/**
 *
 */
public interface StreamContext extends Closeable {

    /**
     * Write a message.  The message is sent when the returned {@code MessageOutput} instance is flushed.
     *
     * @return
     * @throws IOException
     */
    MessageOutput writeMessage() throws IOException;

    /**
     * Indicate that this stream is exhausted.
     *
     * @throws IOException if the notification did not succeed
     */
    void close() throws IOException;
}
