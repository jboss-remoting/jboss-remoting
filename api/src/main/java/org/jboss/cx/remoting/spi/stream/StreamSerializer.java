package org.jboss.cx.remoting.spi.stream;

import java.io.IOException;

/**
 *
 */
public interface StreamSerializer {
    /**
     * Handle an incoming object from the remote side.
     *
     * @param object
     * @throws IOException if the stream data cannot be handled
     */
    void handleData(Object object) throws IOException;
}
