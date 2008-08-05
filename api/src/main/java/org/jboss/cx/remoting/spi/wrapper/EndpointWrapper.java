package org.jboss.cx.remoting.spi.wrapper;

import java.util.concurrent.ConcurrentMap;
import java.io.IOException;
import java.net.URI;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.ClientSource;
import org.jboss.cx.remoting.SimpleCloseable;
import org.jboss.cx.remoting.ServiceListener;
import org.jboss.cx.remoting.spi.remote.RequestHandler;
import org.jboss.cx.remoting.spi.remote.RequestHandlerSource;
import org.jboss.cx.remoting.spi.remote.Handle;
import org.jboss.xnio.IoFuture;

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
    public <I, O> Handle<RequestHandler> createRequestHandler(final RequestListener<I, O> requestListener) throws IOException {
        return delegate.createRequestHandler(requestListener);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public <I, O> Handle<RequestHandlerSource> createRequestHandlerSource(final RequestListener<I, O> requestListener, final String serviceType, final String groupName) throws IOException {
        return delegate.createRequestHandlerSource(requestListener, serviceType, groupName);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public <I, O> Client<I, O> createClient(final RequestHandler handler) throws IOException {
        return delegate.createClient(handler);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public <I, O> ClientSource<I, O> createClientSource(final RequestHandlerSource handlerSource) throws IOException {
        return delegate.createClientSource(handlerSource);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public <I, O> IoFuture<ClientSource<I, O>> locateService(final URI serviceUri) throws IllegalArgumentException {
        return delegate.locateService(serviceUri);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public SimpleCloseable registerRemoteService(final String serviceType, final String groupName, final String endpointName, final RequestHandlerSource handlerSource, final int metric) throws IllegalArgumentException, IOException {
        return delegate.registerRemoteService(serviceType, groupName, endpointName, handlerSource, metric);
    }

    /**
     * {@inheritDoc}  This implementation calls the same method on the delegate object.
     */
    public SimpleCloseable addServiceListener(final ServiceListener serviceListener, final boolean onlyNew) {
        return delegate.addServiceListener(serviceListener, true);
    }
}
