package org.jboss.cx.remoting;

/**
 *
 */
public interface Closeable<T> extends java.io.Closeable {
    void close() throws RemotingException;

    void addCloseHandler(CloseHandler<T> handler);
}
