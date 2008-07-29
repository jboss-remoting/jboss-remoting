package org.jboss.cx.remoting;

import java.io.IOException;

/**
 * A source for new Remoting contexts.
 *
 * @param <I> the request type
 * @param <O> the reply type
 */
public interface ClientSource<I, O> extends Closeable<ClientSource<I, O>> {
    /**
     * Close the context source.  New contexts may no longer be created after this
     * method is called.  Subsequent calls to this method have no additional effect.
     */
    void close() throws IOException;

    /**
     * Create a new communications context.
     *
     * @return the new context
     */
    Client<I, O> createClient() throws IOException;
}
