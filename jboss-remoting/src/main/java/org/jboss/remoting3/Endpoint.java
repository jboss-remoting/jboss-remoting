package org.jboss.remoting3;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.ConnectionProviderRegistration;
import org.jboss.remoting3.spi.MarshallingProtocol;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.OptionMap;
import org.jboss.marshalling.ClassTable;
import org.jboss.marshalling.ObjectTable;
import org.jboss.marshalling.ClassExternalizerFactory;
import org.jboss.marshalling.ClassResolver;
import org.jboss.marshalling.ObjectResolver;

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
     * <p/>
     * You must have the {@link org.jboss.remoting3.EndpointPermission addServiceListener EndpointPermission} to invoke this method.
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
     * Register a named marshalling protocol.
     * <p/>
     * You must have the {@link org.jboss.remoting3.EndpointPermission addMarshallingProtocol EndpointPermission} to invoke this method.
     *
     * @param name the protocol name
     * @param marshallingProtocol the implementation
     * @return a handle which may be used to remove the registration
     * @throws DuplicateRegistrationException if there is already a protocol registered to that name
     */
    Registration addMarshallingProtocol(String name, MarshallingProtocol marshallingProtocol) throws DuplicateRegistrationException;

    /**
     * Register a named class table for marshalling.
     * <p/>
     * You must have the {@link org.jboss.remoting3.EndpointPermission addMarshallingProtocol EndpointPermission} to invoke this method.
     *
     * @param name the protocol name
     * @param classTable the class table
     * @return a handle which may be used to remove the registration
     * @throws DuplicateRegistrationException if there is already a class table registered to that name
     */
    Registration addUserClassTable(String name, ClassTable classTable) throws DuplicateRegistrationException;

    /**
     * Register a named object table for marshalling.
     * <p/>
     * You must have the {@link org.jboss.remoting3.EndpointPermission addMarshallingProtocol EndpointPermission} to invoke this method.
     *
     * @param name the protocol name
     * @param objectTable the object table
     * @return a handle which may be used to remove the registration
     * @throws DuplicateRegistrationException if there is already an object table registered to that name
     */
    Registration addUserObjectTable(String name, ObjectTable objectTable) throws DuplicateRegistrationException;

    /**
     * Register a named class externalizer factory for marshalling.
     * <p/>
     * You must have the {@link org.jboss.remoting3.EndpointPermission addMarshallingProtocol EndpointPermission} to invoke this method.
     *
     * @param name the protocol name
     * @param classExternalizerFactory the class externalizer factory
     * @return a handle which may be used to remove the registration
     * @throws DuplicateRegistrationException if there is already a class externalizer factory registered to that name
     */
    Registration addUserExternalizerFactory(String name, ClassExternalizerFactory classExternalizerFactory) throws DuplicateRegistrationException;

    /**
     * Register a named class resolver for marshalling.
     * <p/>
     * You must have the {@link org.jboss.remoting3.EndpointPermission addMarshallingProtocol EndpointPermission} to invoke this method.
     *
     * @param name the protocol name
     * @param classResolver the class resolver
     * @return a handle which may be used to remove the registration
     * @throws DuplicateRegistrationException if there is already a class resolver registered to that name
     */
    Registration addUserClassResolver(String name, ClassResolver classResolver) throws DuplicateRegistrationException;

    /**
     * Register a named object resolver for marshalling.
     * <p/>
     * You must have the {@link org.jboss.remoting3.EndpointPermission addMarshallingProtocol EndpointPermission} to invoke this method.
     *
     * @param name the protocol name
     * @param objectResolver the class resolver
     * @return a handle which may be used to remove the registration
     * @throws DuplicateRegistrationException if there is already an object resolver registered to that name
     */
    Registration addUserObjectResolver(String name, ObjectResolver objectResolver) throws DuplicateRegistrationException;

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
