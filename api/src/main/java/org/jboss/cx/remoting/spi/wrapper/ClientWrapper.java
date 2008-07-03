package org.jboss.cx.remoting.spi.wrapper;

import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.FutureReply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;

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
    public void close() throws RemotingException {
        delegate.close();
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public void addCloseHandler(final CloseHandler<Client<I, O>> closeHandler) {
        delegate.addCloseHandler(new CloseHandler<Client<I, O>>() {
            public void handleClose(final Client<I, O> closed) {
                closeHandler.handleClose(ClientWrapper.this);
            }
        });
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public O invoke(final I request) throws RemotingException, RemoteExecutionException {
        return delegate.invoke(request);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public FutureReply<O> send(final I request) throws RemotingException {
        return delegate.send(request);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public void sendOneWay(final I request) throws RemotingException {
        delegate.sendOneWay(request);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public ConcurrentMap<Object, Object> getAttributes() {
        return delegate.getAttributes();
    }
}
