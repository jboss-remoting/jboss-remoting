package org.jboss.remoting3;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.ConnectionProviderRegistration;
import org.jboss.xnio.IoFuture;

/**
 * A potential participant in a JBoss Remoting communications relationship.
 * <p/>
 * This interface is part of the Remoting public API.  It is intended to be consumed by Remoting applications; it is
 * not intended to be implemented by them.  Methods may be added to this interface in future minor releases without
 * advance notice.
 *
 * @apiviz.landmark
 */
public interface Endpoint extends HandleableCloseable<Endpoint> {

    /**
     * Get the name of this endpoint.
     *
     * @return the endpoint name, or {@code null} if there is no name
     */
    String getName();

    /**
     * Create a request handler that can be used to receive incoming requests on this endpoint.  The client may be passed to a
     * remote endpoint as part of a request or a reply, or it may be used locally.
     * <p/>
     * You must have the {@link org.jboss.remoting3.EndpointPermission createRequestHandler EndpointPermission} to invoke this method.
     *
     * @param requestListener the request listener
     * @param requestClass the class of requests sent to this request listener
     * @param replyClass the class of replies received back from this request listener
     * @return a handle for the client
     * @throws IOException if an error occurs
     */
    <I, O> RequestHandler createLocalRequestHandler(RequestListener<I, O> requestListener, final Class<I> requestClass, final Class<O> replyClass) throws IOException;

    /**
     * Create a request handler source that can be used to acquire clients associated with a request listener on this endpoint.
     * <p/>
     * You must have the {@link org.jboss.remoting3.EndpointPermission registerService EndpointPermission} to invoke this method.
     *
     * @param configuration the configuration to use
     * @throws IOException if an error occurs
     */
    <I, O> SimpleCloseable registerService(LocalServiceConfiguration<I, O> configuration) throws IOException;

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
     * <p/>
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
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     *
     * @param destination the destination
     * @param connectOptions options to configure this connection
     * @return the future connection
     * @throws IOException if an error occurs while starting the connect attempt
     */
    IoFuture<? extends Connection> connect(URI destination, OptionMap connectOptions) throws IOException;

    /**
     * Register a connection provider for a URI scheme.  The provider factory is called with the context which can
     * be used to accept new connections or terminate the registration.
     *
     * @param uriScheme the URI scheme
     * @param providerFactory the provider factory
     * @return a handle which may be used to remove the registration
     */
    <T> ConnectionProviderRegistration<T> addConnectionProvider(String uriScheme, ConnectionProviderFactory<T> providerFactory);

    /**
     * Flags which can be passed in to listener registration methods.
     */
    enum ListenerFlag {

        /**
         * Include old registrations.
         */
        INCLUDE_OLD,
    }
}
