package org.jboss.cx.remoting.spi.wrapper;

import java.net.URI;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.ClientSource;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.SessionListener;
import org.jboss.cx.remoting.util.AttributeMap;

/**
 * A simple delegating wrapper for endpoints.
 */
public class EndpointWrapper implements Endpoint {
    protected final Endpoint delegate;

    /**
     * Construct a new instance.  Calls will be sent to the given {@code delegate} by default.
     *
     * @param delegate the delegate client instance
     */
    protected EndpointWrapper(final Endpoint delegate) {
        this.delegate = delegate;
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
    public Session openSession(final URI remoteUri, final AttributeMap attributeMap, final RequestListener<?, ?> rootListener) throws RemotingException {
        return delegate.openSession(remoteUri, attributeMap, rootListener);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public String getName() {
        return delegate.getName();
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public <I, O> Client<I, O> createClient(final RequestListener<I, O> requestListener) {
        return delegate.createClient(requestListener);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public <I, O> ClientSource<I, O> createService(final RequestListener<I, O> requestListener) {
        return delegate.createService(requestListener);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public void addSessionListener(final SessionListener sessionListener) {
        delegate.addSessionListener(sessionListener);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public void removeSessionListener(final SessionListener sessionListener) {
        delegate.removeSessionListener(sessionListener);
    }
}
