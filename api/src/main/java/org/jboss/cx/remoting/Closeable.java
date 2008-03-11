package org.jboss.cx.remoting;

/**
 *
 */
public interface Closeable<T> extends java.io.Closeable {
    // TODO - do we need an async close method?

    /**
     * Close, waiting for any outstanding processing to finish.
     *
     * @throws RemotingException if the close failed
     */
    void close() throws RemotingException;

    /**
     * Close immediately.  Any outstanding processing is immediately aborted.
     *
     * @throws RemotingException if the close failed
     */
    void closeImmediate() throws RemotingException;

    /**
     * Add a handler that will be called upon close.  The handler may be called before or after the close acutally
     * takes place.
     *
     * @param handler the close handler
     */
    void addCloseHandler(CloseHandler<T> handler);
}
