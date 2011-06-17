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
import org.jboss.remoting3.security.RemotingPermission;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

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
     * Register a new service.
     *
     * @param serviceType the service type
     * @param openListener the channel open listener
     * @param optionMap the option map
     * @return the service registration which may be closed to remove the service
     * @throws ServiceRegistrationException if the service could not be registered
     */
    Registration registerService(String serviceType, OpenListener openListener, OptionMap optionMap) throws ServiceRegistrationException;

    /**
     * Create a channel pair which are connected to one another.
     *
     * @return the channel pair
     */
    ChannelPair createChannelPair();

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @return the future connection
     * @throws IOException if an error occurs while starting the connect attempt
     */
    IoFuture<Connection> connect(URI destination) throws IOException;

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param connectOptions options to configure this connection
     * @return the future connection
     * @throws IOException if an error occurs while starting the connect attempt
     */
    IoFuture<Connection> connect(URI destination, OptionMap connectOptions) throws IOException;

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * The given callback handler is used to retrieve local authentication information, if the protocol demands it.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param connectOptions options to configure this connection
     * @param callbackHandler the local callback handler to use for authentication
     * @return the future connection
     * @throws IOException if an error occurs while starting the connect attempt
     */
    IoFuture<Connection> connect(URI destination, OptionMap connectOptions, CallbackHandler callbackHandler) throws IOException;

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * The given user name and password is used as local authentication information, if the protocol demands it.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param connectOptions options to configure this connection
     * @param userName the user name to authenticate as, or {@code null} if it is unspecified
     * @param realmName the user realm to authenticate with, or {@code null} if it is unspecified
     * @param password the password to send, or {@code null} if it is unspecified
     * @return the future connection
     * @throws IOException if an error occurs while starting the connect attempt
     */
    IoFuture<Connection> connect(URI destination, OptionMap connectOptions, String userName, String realmName, char[] password) throws IOException;

    /**
     * Register a connection provider for a URI scheme.  The provider factory is called with the context which can
     * be used to accept new connections or terminate the registration.
     * <p/>
     * You must have the {@link RemotingPermission addConnectionProvider EndpointPermission} to invoke this method.
     *
     * @param uriScheme the URI scheme
     * @param providerFactory the provider factory
     * @param optionMap the configuration options for the connection provider
     * @return a handle which may be used to remove the registration
     * @throws IOException if the provider failed to initialize
     * @throws DuplicateRegistrationException if there is already a provider registered to that URI scheme
     */
    Registration addConnectionProvider(String uriScheme, ConnectionProviderFactory providerFactory, OptionMap optionMap) throws DuplicateRegistrationException, IOException;

    /**
     * Get the interface for a connection provider.
     * <p/>
     * You must have the {@link RemotingPermission getConnectionProviderInterface EndpointPermission} to invoke this method.
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
     * Flags which can be passed in to listener registration methods.
     */
    enum ListenerFlag {

        /**
         * Include old registrations.
         */
        INCLUDE_OLD,
    }
}
