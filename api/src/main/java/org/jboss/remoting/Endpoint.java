package org.jboss.remoting;

import java.util.concurrent.ConcurrentMap;
import java.net.URI;
import java.io.IOException;
import org.jboss.remoting.spi.remote.RequestHandler;
import org.jboss.remoting.spi.remote.RequestHandlerSource;
import org.jboss.remoting.spi.remote.Handle;
import org.jboss.xnio.IoFuture;

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
     * @param serviceType the type of service to advertise
     * @param groupName the name of the group of this type to be part of
     * @return a handle for the client source
     * @throws IOException if an error occurs
     */
    <I, O> Handle<RequestHandlerSource> createRequestHandlerSource(RequestListener<I, O> requestListener, String serviceType, String groupName) throws IOException;

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

    /**
     * Attempt to locate a service.  The return value then be queried for the service's {@code ClientSource}.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param serviceUri the URI of the service
     * @return the future service
     * @throws IllegalArgumentException if the given URI is not a valid Remoting service URI
     */
    <I, O> IoFuture<ClientSource<I, O>> locateService(URI serviceUri) throws IllegalArgumentException;

    /**
     * Register a remotely available service.<p>
     * The remote endpoint may not have the same name as this endpoint.  The group name and service type must be
     * non-{@code null} and non-empty.  The metric must be greater than zero.
     *
     * @param serviceType the service type string
     * @param groupName the group name
     * @param endpointName the name of the remote endpoint
     * @param handlerSource the remote handler source
     * @param metric the preference metric, lower is more preferred
     * @return a handle corresponding to the registration
     * @throws IllegalArgumentException if one of the given arguments was not valid
     * @throws IOException if an error occurs with the registration
     */
    SimpleCloseable registerRemoteService(String serviceType, String groupName, String endpointName, RequestHandlerSource handlerSource, int metric) throws IllegalArgumentException, IOException;

    /**
     * Add a listener for observing when local and remote services are added.  The caller may specify whether the listener
     * should be notified of the complete list of currently registered services (set {@code onlyNew} to {@code false})
     * or only services registered after the time of calling this method (set {@code onlyNew} to {@code true}).
     *
     * @param serviceListener the listener
     * @param onlyNew {@code true} if only new registrations should be sent to the listener
     * @return a handle which may be used to unregister the listener
     */
    SimpleCloseable addServiceListener(ServiceListener serviceListener, boolean onlyNew);
}
