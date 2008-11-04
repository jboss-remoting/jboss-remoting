package org.jboss.remoting;

import java.util.concurrent.ConcurrentMap;
import java.net.URI;
import java.io.IOException;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.remoting.spi.Handle;
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
     * You must have the {@link org.jboss.remoting.EndpointPermission createRequestHandler EndpointPermission} to invoke this method.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param requestListener the request listener
     * @param requestClass the class of requests sent to this request listener
     * @param replyClass the class of replies received back from this request listener
     * @return a handle for the client
     * @throws IOException if an error occurs
     */
    <I, O> Handle<RequestHandler> createRequestHandler(RequestListener<I, O> requestListener, final Class<I> requestClass, final Class<O> replyClass) throws IOException;

    /**
     * Create a request handler source that can be used to acquire clients associated with a request listener on this endpoint.
     * The request handler source may be ignored, passed to a remote endpoint as part of a request or a reply, or used locally.
     * The objects that are produced by this method may be used to mass-produce {@code RequestHandler} instances.
     *
     * You must have the {@link org.jboss.remoting.EndpointPermission registerService EndpointPermission} to invoke this method.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param configuration the configuration to use
     * @throws IOException if an error occurs
     */
    <I, O> Handle<RequestHandlerSource> registerService(LocalServiceConfiguration<I, O> configuration) throws IOException;

    /**
     * Create a client that uses the given request handler to handle its requests.
     *
     * You must have the {@link org.jboss.remoting.EndpointPermission createClient EndpointPermission} to invoke this method.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param handler the request handler
     * @param requestClass the class of requests sent through this client
     * @param replyClass the class of replies received back through this client
     * @return the client
     * @throws IOException if an error occurs
     */
    <I, O> Client<I, O> createClient(RequestHandler handler, Class<I> requestClass,  Class<O> replyClass) throws IOException;

    /**
     * Create a client source that uses the given request handler source to generate clients.
     *
     * You must have the {@link org.jboss.remoting.EndpointPermission createClientSource EndpointPermission} to invoke this method.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param handlerSource the request handler source
     * @param requestClass the class of requests sent through this client source
     * @param replyClass the class of replies received back through this client source
     * @return the client source
     * @throws IOException if an error occurs
     */
    <I, O> ClientSource<I, O> createClientSource(RequestHandlerSource handlerSource, Class<I> requestClass, Class<O> replyClass) throws IOException;

    /**
     * Attempt to locate a service.  The return value then be queried for the service's {@code ClientSource}.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param serviceUri the URI of the service
     * @param requestClass the class of requests sent through the client source
     * @param replyClass the class of replies received back through the client source
     * @return the future service
     * @throws IllegalArgumentException if the given URI is not a valid Remoting service URI
     */
    <I, O> IoFuture<ClientSource<I, O>> locateService(URI serviceUri, Class<I> requestClass, Class<O> replyClass) throws IllegalArgumentException;

    /**
     * Register a remotely available service.<p>
     * The remote endpoint may not have the same name as this endpoint.  The group name and service type must be
     * non-{@code null} and non-empty.  The metric must be greater than zero.
     *
     * You must have the {@link org.jboss.remoting.EndpointPermission registerRemoteService EndpointPermission} to invoke this method.
     *
     * @param configuration the remote service configuration
     * @throws IllegalArgumentException if one of the given arguments was not valid
     * @throws IOException if an error occurs with the registration
     */
    SimpleCloseable registerRemoteService(RemoteServiceConfiguration configuration) throws IllegalArgumentException, IOException;

    /**
     * Add a listener for observing when local and remote services are added.  The caller may specify whether the listener
     * should be notified of the complete list of currently registered services (set {@code onlyNew} to {@code false})
     * or only services registered after the time of calling this method (set {@code onlyNew} to {@code true}).
     *
     * You must have the {@link org.jboss.remoting.EndpointPermission addServiceListener EndpointPermission} to invoke this method.
     *
     * @param serviceListener the listener
     * @param onlyNew {@code true} if only new registrations should be sent to the listener
     * @return a handle which may be used to unregister the listener
     */
    SimpleCloseable addServiceListener(ServiceListener serviceListener, boolean onlyNew);
}
