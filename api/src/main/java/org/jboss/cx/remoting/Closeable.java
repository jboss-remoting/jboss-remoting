package org.jboss.cx.remoting;

import java.io.IOException;

/**
 * A Remoting resource that can be closed.
 *
 * @param <T> the type that is passed to the close handler
 */
public interface Closeable<T> extends java.io.Closeable {

    /**
     * Close, waiting for any outstanding processing to finish.
     *
     * @throws IOException if the close failed
     */
    void close() throws IOException;

    /**
     * Add a handler that will be called upon close.  The handler may be called before or after the close acutally
     * takes place.
     *
     * @param handler the close handler
     */
    void addCloseHandler(CloseHandler<? super T> handler);
}
