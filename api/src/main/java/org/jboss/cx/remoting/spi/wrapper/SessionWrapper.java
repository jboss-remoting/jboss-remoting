package org.jboss.cx.remoting.spi.wrapper;

import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Session;

/**
 * A simple delegating wrapper for clients.
 */
public class SessionWrapper implements Session {
    protected final Session delegate;

    /**
     * Construct a new instance.  Calls will be sent to the given {@code delegate} by default.
     *
     * @param delegate the delegate client instance
     */
    protected SessionWrapper(final Session delegate) {
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
    public void addCloseHandler(final CloseHandler<Session> closeHandler) {
        delegate.addCloseHandler(new CloseHandler<Session>() {
            public void handleClose(final Session closed) {
                closeHandler.handleClose(SessionWrapper.this);
            }
        });
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public ConcurrentMap<Object, Object> getAttributes() {
        return delegate.getAttributes();
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public String getLocalEndpointName() {
        return delegate.getLocalEndpointName();
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public String getRemoteEndpointName() {
        return delegate.getRemoteEndpointName();
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public <I, O> Client<I, O> getRootClient() {
        return delegate.getRootClient();
    }
}
