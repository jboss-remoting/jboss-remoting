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
import org.wildfly.common.selector.DefaultSelector;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

import javax.security.sasl.SaslClientFactory;

/**
 * A potential participant in a JBoss Remoting communications relationship.
 * <p/>
 * This interface is part of the Remoting public API.  It is intended to be consumed by Remoting applications; it is
 * not intended to be implemented by them.  Methods may be added to this interface in future minor releases without
 * advance notice.
 *
 * @apiviz.landmark
 */
@DefaultSelector(ConfigurationEndpointSelector.class)
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
     * Get a possibly pre-existing connection to the destination.
     *
     * @param destination the destination URI
     * @return the future (or existing) connection
     * @throws IOException if an error occurs while starting a connect attempt
     */
    IoFuture<Connection> getConnection(URI destination) throws IOException;

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     *
     * @return the future connection
     *
     * @throws IOException if an error occurs while starting the connect attempt
     */
    default IoFuture<Connection> connect(URI destination) throws IOException {
        return connect(destination, OptionMap.EMPTY);
    }

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param connectOptions options to configure this connection
     *
     * @return the future connection
     *
     * @throws IOException if an error occurs while starting the connect attempt
     */
    IoFuture<Connection> connect(URI destination, OptionMap connectOptions) throws IOException;

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param connectOptions options to configure this connection
     * @param saslClientFactory the SASL client factory to use for client authentication
     *
     * @return the future connection
     *
     * @throws IOException if an error occurs while starting the connect attempt
     */
    IoFuture<Connection> connect(URI destination, OptionMap connectOptions, SaslClientFactory saslClientFactory) throws IOException;

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param connectOptions options to configure this connection
     * @param authenticationContext the client authentication context to use
     *
     * @return the future connection
     *
     * @throws IOException if an error occurs while starting the connect attempt
     */
    IoFuture<Connection> connect(URI destination, OptionMap connectOptions, AuthenticationContext authenticationContext) throws IOException;

    /**
     * Open a connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param connectOptions options to configure this connection
     * @param authenticationContext the client authentication context to use
     * @param saslClientFactory the SASL client factory to use for client authentication
     *
     * @return the future connection
     *
     * @throws IOException if an error occurs while starting the connect attempt
     */
    IoFuture<Connection> connect(URI destination, OptionMap connectOptions, AuthenticationContext authenticationContext, SaslClientFactory saslClientFactory) throws IOException;

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
     * Determine whether the given URI scheme is valid for this endpoint.
     *
     * @param uriScheme the URI scheme
     * @return {@code true} if the URI scheme is valid at the time this method is called
     */
    boolean isValidUriScheme(String uriScheme);

    /**
     * Get the XNIO worker configured for this endpoint.
     *
     * @return the XNIO worker
     */
    XnioWorker getXnioWorker();

    /**
     * Create a new endpoint builder.
     *
     * @return the new endpoint builder
     */
    static EndpointBuilder builder() {
        return new EndpointBuilder();
    }
}
