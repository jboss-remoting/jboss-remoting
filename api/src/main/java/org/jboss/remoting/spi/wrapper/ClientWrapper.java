package org.jboss.remoting.spi.wrapper;

import java.util.concurrent.ConcurrentMap;
import java.io.IOException;
import org.jboss.remoting.CloseHandler;
import org.jboss.remoting.Client;
import org.jboss.xnio.IoFuture;

/**
 * A simple delegating wrapper for clients.
 *
 * @param <I> the request type
 * @param <O> the reply type
 */
public class ClientWrapper<I, O> implements Client<I, O> {
    protected final Client<I, O> delegate;

    /**
     * Construct a new instance.  Calls will be sent to the given {@code delegate} by default.
     *
     * @param delegate the delegate client instance
     */
    protected ClientWrapper(final Client<I, O> delegate) {
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
    public void addCloseHandler(final CloseHandler<? super Client<I, O>> closeHandler) {
        delegate.addCloseHandler(new CloseHandler<Client<I, O>>() {
            public void handleClose(final Client<I, O> closed) {
                closeHandler.handleClose(ClientWrapper.this);
            }
        });
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public O invoke(final I request) throws IOException {
        return delegate.invoke(request);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public IoFuture<O> send(final I request) throws IOException {
        return delegate.send(request);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public ConcurrentMap<Object, Object> getAttributes() {
        return delegate.getAttributes();
    }
}
