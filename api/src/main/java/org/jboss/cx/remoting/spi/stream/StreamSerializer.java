package org.jboss.cx.remoting.spi.stream;

import java.io.IOException;
import org.jboss.cx.remoting.util.ObjectMessageInput;

/**
 *
 */
public interface StreamSerializer {
    /**
     * Handle the startup of the stream.
     *
     * @throws IOException if an error occurs
     */
    void handleOpen() throws IOException;

    /**
     * Handle an incoming message from the remote side.
     *
     * @param data the message
     * @throws IOException if the stream data cannot be handled
     */
    void handleData(ObjectMessageInput data) throws IOException;

    /**
     * Handle a close from the remote side.
     *
     * @throws IOException if an error occurs
     */
    void handleClose() throws IOException;
}
