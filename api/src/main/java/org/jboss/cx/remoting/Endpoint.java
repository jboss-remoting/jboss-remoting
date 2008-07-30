package org.jboss.cx.remoting;

import java.util.concurrent.ConcurrentMap;
import java.io.IOException;
import org.jboss.cx.remoting.spi.remote.RequestHandler;
import org.jboss.cx.remoting.spi.remote.RequestHandlerSource;
import org.jboss.cx.remoting.spi.remote.Handle;

/**
 * A potential participant in a JBoss Remoting communications relationship.
 */
public interface Endpoint {
    /**
     * Get the endpoint attribute map.  This is a storage area for any data associated with this endpoint, including
     * (but not limited to) connection and protocol information, and application information.
     *
     * You must have the TODO permission to invoke this method.
     *
     * @return the endpoint map
     */
    ConcurrentMap<Object, Object> getAttributes();

    /**
     * Get the name of this endpoint.
     *
     * @return the endpoint name, or {@code null} if there is no name
     */
    String getName();

    /**
     * Create a request handler that can be used to receive incoming requests on this endpoint.  The client may be passed to a
     * remote endpoint as part of a request or a reply, or it may be used locally.
     *
     * You must have the TODO permission to invoke this method.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param requestListener the request listener
     * @return a handle for the client
     * @throws IOException if an error occurs
     */
    <I, O> Handle<RequestHandler> createRequestHandler(RequestListener<I, O> requestListener) throws IOException;

    /**
     * Create a request handler source that can be used to acquire clients associated with a request listener on this endpoint.
     * The request handler source may be passed to a remote endpoint as part of a request or a reply, or it may be used locally.
     * The objects that are produced by this method may be used to mass-produce {@code RequestHandler} instances.
     *
     * You must have the TODO permission to invoke this method.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param requestListener the request listener
     * @return a handle for the client source
     * @throws IOException if an error occurs
     */
    <I, O> Handle<RequestHandlerSource> createRequestHandlerSource(RequestListener<I, O> requestListener) throws IOException;

    /**
     * Create a client that uses the given request handler to handle its requests.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param handler the request handler
     * @return the client
     * @throws IOException if an error occurs
     */
    <I, O> Client<I, O> createClient(RequestHandler handler) throws IOException;

    /**
     * Create a client source that uses the given request handler source to generate clients.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param handlerSource the request handler source
     * @return the client source
     * @throws IOException if an error occurs
     */
    <I, O> ClientSource<I, O> createClientSource(RequestHandlerSource handlerSource) throws IOException;
}
