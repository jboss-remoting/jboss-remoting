package org.jboss.cx.remoting;

/**
 * A source for new Remoting contexts.
 */
public interface ContextSource<I, O> extends Closeable<ContextSource<I, O>> {
    /**
     * Close the context source.  New contexts may no longer be created after this
     * method is called.  Subsequent calls to this method have no additional effect.
     */
    void close() throws RemotingException;

    /**
     * Create a new communications context.
     *
     * @return the new context
     */
    Context<I, O> createContext() throws RemotingException;
}
