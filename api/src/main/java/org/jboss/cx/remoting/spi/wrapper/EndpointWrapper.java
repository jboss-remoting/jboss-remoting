package org.jboss.cx.remoting.spi.wrapper;

import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.ClientSource;
import org.jboss.cx.remoting.spi.remote.RequestHandler;
import org.jboss.cx.remoting.spi.remote.RequestHandlerSource;
import org.jboss.cx.remoting.spi.remote.Handle;

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
    public String getName() {
        return delegate.getName();
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public <I, O> Handle<RequestHandler> createRequestHandler(final RequestListener<I, O> requestListener) throws RemotingException {
        return delegate.createRequestHandler(requestListener);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public <I, O> Handle<RequestHandlerSource> createRequestHandlerSource(final RequestListener<I, O> requestListener) throws RemotingException {
        return delegate.createRequestHandlerSource(requestListener);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public <I, O> Client<I, O> createClient(final RequestHandler handler) throws RemotingException {
        return delegate.createClient(handler);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public <I, O> ClientSource<I, O> createClientSource(final RequestHandlerSource handlerSource) throws RemotingException {
        return delegate.createClientSource(handlerSource);
    }
}
