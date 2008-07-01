package org.jboss.cx.remoting.spi.wrapper;

import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.ClientSource;
import org.jboss.cx.remoting.RemotingException;

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
    public void close() throws RemotingException {
        delegate.close();
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public void addCloseHandler(final CloseHandler<ClientSource<I, O>> closeHandler) {
        delegate.addCloseHandler(new CloseHandler<ClientSource<I, O>>() {
            public void handleClose(final ClientSource<I, O> closed) {
                closeHandler.handleClose(ClientSourceWrapper.this);
            }
        });
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public Client<I, O> createContext() throws RemotingException {
        return delegate.createContext();
    }
}
