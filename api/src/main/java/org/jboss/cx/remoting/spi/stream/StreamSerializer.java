package org.jboss.cx.remoting.spi.stream;

import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.MessageInput;

/**
 *
 */
public interface StreamSerializer {
    /**
     * Handle an incoming object from the remote side.
     *
     * @param data
     * @throws IOException if the stream data cannot be handled
     */
    void handleData(MessageInput data) throws IOException;

    /**
     * Handle a close from the remote side.
     *
     * @throws IOException if an error occurs
     */
    void handleClose() throws IOException;
}
