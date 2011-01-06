/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.ProtocolServiceType;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.OptionMap;

import javax.security.auth.callback.CallbackHandler;

/**
 * A potential participant in a JBoss Remoting communications relationship.
 * <p/>
 * This interface is part of the Remoting public API.  It is intended to be consumed by Remoting applications; it is
 * not intended to be implemented by them.  Methods may be added to this interface in future minor releases without
 * advance notice.
 *
 * @apiviz.landmark
 */
public interface Endpoint extends HandleableCloseable<Endpoint>, Attachable {

    /**
     * Get the name of this endpoint.
     *
     * @return the endpoint name, or {@code null} if there is no name
     */
    String getName();

    /**
     * Get a new service builder which can be used to register a service.
     *
     * @return a new service builder
     */
    ServiceBuilder<?, ?> serviceBuilder();

    /**
     * Get a new service builder which can be used to register a service.
     *
     * @param requestClass the request class
     * @param replyClass the reply class
     * @param <I> the request type
     * @param <O> the reply type
     * @return a new service builder
     */
    <I, O> ServiceBuilder<I, O> serviceBuilder(Class<I> requestClass, Class<O> replyClass);

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
         * Set the service class loader.  This class loader will be used to unmarshall incoming requests.
         *
         * @param classLoader the service class loader
         * @return this builder
         */
        ServiceBuilder<I, O> setClassLoader(final ClassLoader classLoader);

        /**
         * Set the option map for the service.  The options may include, but are not limited to:
         * <ul>
         * <li>{@link RemotingOptions#BUFFER_SIZE} - the recommended buffer size for marshallers to use for this service</li>
         * <li>{@link RemotingOptions#CLASS_COUNT} - the recommended class count for marshallers to use for this service</li>
         * <li>{@link RemotingOptions#INSTANCE_COUNT} - the recommended instance count for marshallers to use for this service</li>
         * <li>{@link RemotingOptions#METRIC} - the relative desirability or "distance" of this service</li>
         * <li>{@link RemotingOptions#MARSHALLING_PROTOCOLS} - the marshalling protocols which are allowed for this service,
         *          in order of decreasing preference; if none is given, all registered protocols will
         *          be made available</li>
         * <li>{@link RemotingOptions#MARSHALLING_CLASS_RESOLVERS} - the class resolvers which are allowed for this service,
         *          in order of decreasing preference; if none is given, the default class resolver is used</li>
         * <li>{@link RemotingOptions#MARSHALLING_CLASS_TABLES} - the class tables which are allowed for this service, in order
         *          of decreasing preference</li>
         * <li>{@link RemotingOptions#MARSHALLING_EXTERNALIZER_FACTORIES} - the class externalizer factories which are allowed
         *          for this service, in order of decreasing preference</li>
         * <li>{@link RemotingOptions#REMOTELY_VISIBLE} - {@code true} if this service should be remotely accessible,
         *          {@code false} otherwise (defaults to {@code true})</li>
         * <li>{@link RemotingOptions#REQUIRE_SECURE} - {@code true} if this service may only be accessed over a secure/encrypted
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
         * You must have the {@link org.jboss.remoting3.security.RemotingPermission registerService EndpointPermission} to invoke this method.
         *
         * @return a registration handle
         * @throws IOException if a problem occurs with registration
         */
        Registration register() throws IOException;
    }

    /**
     * Add a service registration listener which is called whenever a local service is registered.
     * <p/>
     * You must have the {@link org.jboss.remoting3.security.RemotingPermission addServiceListener EndpointPermission} to invoke this method.
     *
     * @param listener the listener
     * @param flags the flags to apply to the listener
     * @return a handle which may be used to remove the listener registration
     */
    Registration addServiceRegistrationListener(ServiceRegistrationListener listener, Set<ListenerFlag> flags);

    /**
     * Create a local client for a client listener.
     *
     * @param clientListener the client listener
     * @param requestClass the request class
     * @param replyClass the reply class
     * @param clientClassLoader the class loader to use for replies
     * @param optionMap the options
     * @return a new client
     * @throws IOException if an error occurs
     */
    <I, O> Client<I, O> createLocalClient(ClientListener<I, O> clientListener, Class<I> requestClass, Class<O> replyClass, ClassLoader clientClassLoader, OptionMap optionMap) throws IOException;

    /**
     * Create a local client for a client listener.
     *
     * @param clientListener
     * @param requestClass the request class
     * @param replyClass the reply class
     * @param optionMap the options
     * @return a new client
     * @throws IOException if an error occurs
     */
    <I, O> Client<I, O> createLocalClient(ClientListener<I, O> clientListener, Class<I> requestClass, Class<O> replyClass, OptionMap optionMap) throws IOException;

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link org.jboss.remoting3.security.RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @return the future connection
     * @throws IOException if an error occurs while starting the connect attempt
     */
    IoFuture<? extends Connection> connect(URI destination) throws IOException;

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link org.jboss.remoting3.security.RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param connectOptions options to configure this connection
     * @return the future connection
     * @throws IOException if an error occurs while starting the connect attempt
     */
    IoFuture<? extends Connection> connect(URI destination, OptionMap connectOptions) throws IOException;

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * The given callback handler is used to retrieve local authentication information, if the protocol demands it.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link org.jboss.remoting3.security.RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param connectOptions options to configure this connection
     * @param callbackHandler the local callback handler to use for authentication
     * @return the future connection
     * @throws IOException if an error occurs while starting the connect attempt
     */
    IoFuture<? extends Connection> connect(URI destination, OptionMap connectOptions, CallbackHandler callbackHandler) throws IOException;

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * The given user name and password is used as local authentication information, if the protocol demands it.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link org.jboss.remoting3.security.RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param connectOptions options to configure this connection
     * @param userName the user name to authenticate as, or {@code null} if it is unspecified
     * @param realmName the user realm to authenticate with, or {@code null} if it is unspecified
     * @param password the password to send, or {@code null} if it is unspecified
     * @return the future connection
     * @throws IOException if an error occurs while starting the connect attempt
     */
    IoFuture<? extends Connection> connect(URI destination, OptionMap connectOptions, String userName, String realmName, char[] password) throws IOException;

    /**
     * Register a connection provider for a URI scheme.  The provider factory is called with the context which can
     * be used to accept new connections or terminate the registration.
     * <p/>
     * You must have the {@link org.jboss.remoting3.security.RemotingPermission addConnectionProvider EndpointPermission} to invoke this method.
     *
     * @param uriScheme the URI scheme
     * @param providerFactory the provider factory
     * @return a handle which may be used to remove the registration
     * @throws DuplicateRegistrationException if there is already a provider registered to that URI scheme
     */
    Registration addConnectionProvider(String uriScheme, ConnectionProviderFactory providerFactory) throws DuplicateRegistrationException;

    /**
     * Get the interface for a connection provider.
     * <p/>
     * You must have the {@link org.jboss.remoting3.security.RemotingPermission getConnectionProviderInterface EndpointPermission} to invoke this method.
     *
     * @param uriScheme the URI scheme of the registered connection provider
     * @param expectedType the expected type of the interface
     * @param <T> the expected type of the interface
     * @return the provider interface
     * @throws UnknownURISchemeException if the given URI scheme is not registered
     * @throws ClassCastException if the interface type does not match the expected type
     */
    <T> T getConnectionProviderInterface(String uriScheme, Class<T> expectedType) throws UnknownURISchemeException, ClassCastException;

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
