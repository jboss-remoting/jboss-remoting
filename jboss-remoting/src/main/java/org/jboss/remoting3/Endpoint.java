package org.jboss.remoting3;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import org.jboss.remoting3.spi.Handle;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.RequestHandlerSource;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.xnio.IoFuture;

/**
 * A potential participant in a JBoss Remoting communications relationship.
 *
 * @apiviz.landmark
 */
public interface Endpoint extends HandleableCloseable<Endpoint> {
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
     * You must have the {@link org.jboss.remoting3.EndpointPermission createRequestHandler EndpointPermission} to invoke this method.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param requestListener the request listener
     * @param requestClass the class of requests sent to this request listener
     * @param replyClass the class of replies received back from this request listener
     * @return a handle for the client
     * @throws IOException if an error occurs
     */
    <I, O> Handle<RequestHandler> createLocalRequestHandler(RequestListener<I, O> requestListener, final Class<I> requestClass, final Class<O> replyClass) throws IOException;

    /**
     * Create a request handler source that can be used to acquire clients associated with a request listener on this endpoint.
     * The request handler source may be ignored, passed to a remote endpoint as part of a request or a reply, or used locally.
     * The objects that are produced by this method may be used to mass-produce {@code RequestHandler} instances.
     *
     * You must have the {@link org.jboss.remoting3.EndpointPermission registerService EndpointPermission} to invoke this method.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param configuration the configuration to use
     * @throws IOException if an error occurs
     */
    <I, O> Handle<RequestHandlerSource> registerService(LocalServiceConfiguration<I, O> configuration) throws IOException;

    /**
     * Add a service registration listener which is called whenever a local service is registered.
     *
     * @param listener the listener
     * @param flags the flags to apply to the listener
     * @return a handle which may be used to remove the listener registration
     */
    SimpleCloseable addServiceRegistrationListener(ServiceRegistrationListener listener, Set<ListenerFlag> flags);

    /**
     * Create a client that uses the given request handler to handle its requests.
     *
     * You must have the {@link org.jboss.remoting3.EndpointPermission createClient EndpointPermission} to invoke this method.
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
     * You must have the {@link org.jboss.remoting3.EndpointPermission createClientSource EndpointPermission} to invoke this method.
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
     * Attempt to open a client source by URI.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param uri the URI of the service
     * @param requestClass the class of requests sent through the client source
     * @param replyClass the class of replies received back through the client source
     * @return the future service
     * @throws IllegalArgumentException if the URI scheme does not correspond to a client souerce connection provider
     */
    <I, O> IoFuture<? extends ClientSource<I, O>> openClientSource(URI uri, Class<I> requestClass, Class<O> replyClass) throws IllegalArgumentException;

    /**
     * Attempt to open a client by URI.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param uri the URI of the service
     * @param requestClass the class of requests sent through the client source
     * @param replyClass the class of replies received back through the client source
     * @return the future service
     * @throws IllegalArgumentException if the URI scheme does not correspond to a client connection provider
     */
    <I, O> IoFuture<? extends Client<I, O>> openClient(URI uri, Class<I> requestClass, Class<O> replyClass) throws IllegalArgumentException;

    /**
     * Connect to a remote endpoint.
     *
     * @param endpointUri the URI of the endpoint to connect to
     * @return the future connection
     * @throws IllegalArgumentException if the URI scheme does not correspond to an endpoint connection provider
     */
    IoFuture<? extends Closeable> openEndpointConnection(URI endpointUri) throws IllegalArgumentException;

    /**
     * Register a connection provider for a URI scheme.
     *
     * @param uriScheme the URI scheme
     * @param provider the provider
     * @return a handle which may be used to remove the registration
     */
    SimpleCloseable addConnectionProvider(String uriScheme, ConnectionProvider<?> provider);

    /**
     * Get the type of resource specified by the given URI.  If the type cannot be determined, returns {@link org.jboss.remoting3.ResourceType#UNKNOWN UNKNOWN}.
     *
     * @param uri the connection URI
     * @return the resource type
     */
    ResourceType getResourceType(URI uri);


    enum ListenerFlag {

        /**
         * Include old registrations.
         */
        INCLUDE_OLD,
    }
}
