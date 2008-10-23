package org.jboss.remoting;

import org.jboss.remoting.CloseHandler;
import org.jboss.remoting.Client;
import org.jboss.remoting.ClientSource;
import java.io.IOException;

/**
 * A simple delegating wrapper for client sources.
 *
 * @param <I> the request type
 * @param <O> the reply type
 */
public class ClientSourceWrapper<I, O> implements ClientSource<I, O> {
    private final ClientSource<I, O> delegate;

    /**
     * Construct a new instance.  Calls will be sent to the given {@code delegate} by default.
     *
     * @param delegate the delegate client instance
     */
    protected ClientSourceWrapper(ClientSource<I, O> delegate) {
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public void close() throws IOException {
        delegate.close();
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public void addCloseHandler(final CloseHandler<? super ClientSource<I, O>> closeHandler) {
        delegate.addCloseHandler(new CloseHandler<ClientSource<I, O>>() {
            public void handleClose(final ClientSource<I, O> closed) {
                closeHandler.handleClose(ClientSourceWrapper.this);
            }
        });
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public Client<I, O> createClient() throws IOException {
        return delegate.createClient();
    }
}
