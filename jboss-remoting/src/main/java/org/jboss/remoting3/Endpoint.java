package org.jboss.remoting3;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.ConnectionProviderRegistration;
import org.jboss.remoting3.spi.ProtocolServiceType;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.OptionMap;

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
     * @return the request handler
     * @throws IOException if an error occurs
     */
    <I, O> RequestHandler createLocalRequestHandler(RequestListener<? super I, ? extends O> requestListener, Class<I> requestClass, Class<O> replyClass) throws IOException;

    /**
     * Get a new service builder which can be used to register a service.
     *
     * @return a new service builder
     */
    ServiceBuilder<?, ?> serviceBuilder();

    /**
     * A service builder for new service registration.
     *
     * @param <I> the request type
     * @param <O> the reply type
     */
    interface ServiceBuilder<I, O> {

        /**
         * Set the group name.
         * @param groupName the group name
         * @return this builder
         */
        ServiceBuilder<I, O> setGroupName(String groupName);

        /**
         * Set the service type string, which should follow the convention for package names (reversed domain names).
         *
         * @param serviceType the service type
         * @return this builder
         */
        ServiceBuilder<I, O> setServiceType(String serviceType);

        /**
         * Clear the configured client listener and set a new request type.
         *
         * @param newRequestType the new request type's class
         * @param <N> the new request type
         * @return this builder, cast to include the new request type
         */
        <N> ServiceBuilder<N, O> setRequestType(Class<N> newRequestType);

        /**
         * Clear the configured client listener and set a new reply type.
         *
         * @param newReplyType the new reply type's class
         * @param <N> the new reply type
         * @return this builder, cast to include the new reply type
         */
        <N> ServiceBuilder<I, N> setReplyType(Class<N> newReplyType);

        /**
         * Set the request listener.  The given listener may be configured to accept a superclass of the given
         * request type, or a subclass of the given reply type, since they are compatible.
         *
         * @param clientListener the request listener
         * @return this builder
         */
        ServiceBuilder<I, O> setClientListener(ClientListener<? super I, ? extends O> clientListener);

        /**
         * Set the option map for the service.  The options may include, but are not limited to:
         * <ul>
         * <li>{@link Options#BUFFER_SIZE} - the recommended buffer size for marshallers to use for this service</li>
         * <li>{@link Options#CLASS_COUNT} - the recommended class count for marshallers to use for this service</li>
         * <li>{@link Options#INSTANCE_COUNT} - the recommended instance count for marshallers to use for this service</li>
         * <li>{@link Options#METRIC} - the relative desirability or "distance" of this service</li>
         * <li>{@link Options#MARSHALLING_PROTOCOLS} - the marshalling protocols which are allowed for this service,
         *          in order of decreasing preference; if none is given, all registered protocols will
         *          be made available</li>
         * <li>{@link Options#MARSHALLING_CLASS_RESOLVERS} - the class resolvers which are allowed for this service,
         *          in order of decreasing preference; if none is given, the default class resolver is used</li>
         * <li>{@link Options#MARSHALLING_CLASS_TABLES} - the class tables which are allowed for this service, in order
         *          of decreasing preference</li>
         * <li>{@link Options#MARSHALLING_EXTERNALIZER_FACTORIES} - the class externalizer factories which are allowed
         *          for this service, in order of decreasing preference</li>
         * <li>{@link Options#REMOTELY_VISIBLE} - {@code true} if this service should be remotely accessible,
         *          {@code false} otherwise (defaults to {@code true})</li>
         * <li>{@link Options#REQUIRE_SECURE} - {@code true} if this service may only be accessed over a secure/encrypted
         *          channel; defaults to {@code false}, however this should be set to {@code true} if sensitive data (e.g.
         *          passwords) may be transmitted as part of a payload</li>
         * </ul>
         *
         * @param optionMap the option map
         * @return this builder
         */
        ServiceBuilder<I, O> setOptionMap(OptionMap optionMap);

        /**
         * Register the service.
         * <p/>
         * You must have the {@link org.jboss.remoting3.EndpointPermission registerService EndpointPermission} to invoke this method.
         *
         * @return a registration handle
         * @throws IOException if a problem occurs with registration
         */
        Registration register() throws IOException;
    }

    /**
     * Add a service registration listener which is called whenever a local service is registered.
     * <p/>
     * You must have the {@link org.jboss.remoting3.EndpointPermission addServiceListener EndpointPermission} to invoke this method.
     *
     * @param listener the listener
     * @param flags the flags to apply to the listener
     * @return a handle which may be used to remove the listener registration
     */
    Registration addServiceRegistrationListener(ServiceRegistrationListener listener, Set<ListenerFlag> flags);

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
    <I, O> Client<I, O> createClient(RequestHandler handler, Class<I> requestClass, Class<O> replyClass) throws IOException;

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link org.jboss.remoting3.EndpointPermission connect EndpointPermission} to invoke this method.
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
     * <p/>
     * You must have the {@link org.jboss.remoting3.EndpointPermission addConnectionProvider EndpointPermission} to invoke this method.
     *
     * @param uriScheme the URI scheme
     * @param providerFactory the provider factory
     * @return a handle which may be used to remove the registration
     * @throws DuplicateRegistrationException if there is already a provider registered to that URI scheme
     */
    <T> ConnectionProviderRegistration<T> addConnectionProvider(String uriScheme, ConnectionProviderFactory<T> providerFactory) throws DuplicateRegistrationException;

    /**
     * Register a protocol service.
     *
     * @param type the type of service being registered
     * @param name the name of the protocol provider
     * @param provider the provider instance
     * @param <T> the provider type
     * @return a handle which may be used to remove the registration
     * @throws DuplicateRegistrationException if there is already a protocol registered to that name
     */
    <T> Registration addProtocolService(ProtocolServiceType<T> type, String name, T provider) throws DuplicateRegistrationException;

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
