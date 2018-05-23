/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3;

import static java.security.AccessController.doPrivileged;
import static org.jboss.remoting3.EndpointImpl.AUTH_CONFIGURATION_CLIENT;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;

import org.jboss.remoting3._private.Messages;
import org.jboss.remoting3.security.RemotingPermission;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.wildfly.common.Assert;
import org.wildfly.common.context.ContextManager;
import org.wildfly.common.context.Contextual;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.xnio.FailedIoFuture;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

import javax.net.ssl.SSLContext;
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
public interface Endpoint extends HandleableCloseable<Endpoint>, Attachable, Contextual<Endpoint> {
    /**
     * The context manager for Remoting endpoints.
     */
    ContextManager<Endpoint> ENDPOINT_CONTEXT_MANAGER = doPrivileged((PrivilegedAction<ContextManager<Endpoint>>) () -> {
        final ContextManager<Endpoint> contextManager = new ContextManager<>(Endpoint.class, "jboss-remoting.endpoint");
        contextManager.setGlobalDefaultSupplierIfNotSet(ConfigurationEndpointSupplier::new);
        return contextManager;
    });

    /**
     * Get the context manager for Remoting endpoints ({@link #ENDPOINT_CONTEXT_MANAGER}).
     *
     * @return the context manager for Remoting endpoints (not {@code null})
     */
    default ContextManager<Endpoint> getInstanceContextManager() {
        return EndpointImpl.ENDPOINT_CONTEXT_MANAGER;
    }

    /**
     * Get the currently active Remoting endpoint.  If none is selected, {@code null} is returned.
     *
     * @return the currently active Remoting endpoint, or {@code null} if none
     */
    static Endpoint getCurrent() {
        return EndpointGetterHolder.SUPPLIER.get();
    }

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
     * Get a possibly shared, possibly existing connection to the destination.  The authentication and SSL configuration is selected from
     * the given context with the given abstract type (if specified).
     *
     * @param destination the destination URI (must not be {@code null})
     * @param abstractType the abstract type of the connection (may be {@code null})
     * @param abstractTypeAuthority the authority name of the abstract type of the connection (may be {@code null})
     * @param context the authentication context to use (must not be {@code null})
     * @return the future connection identity (not {@code null})
     */
    default IoFuture<ConnectionPeerIdentity> getConnectedIdentity(URI destination, String abstractType, String abstractTypeAuthority, AuthenticationContext context) {
        Assert.checkNotNullParam("destination", destination);
        Assert.checkNotNullParam("context", context);
        final AuthenticationContextConfigurationClient client = AUTH_CONFIGURATION_CLIENT;
        final SSLContext sslContext;
        try {
            sslContext = client.getSSLContext(destination, context);
        } catch (GeneralSecurityException e) {
            return new FailedIoFuture<>(Messages.conn.failedToConfigureSslContext(e));
        }
        final AuthenticationConfiguration authenticationConfiguration = client.getAuthenticationConfiguration(destination, context, -1, abstractType, abstractTypeAuthority);
        return getConnectedIdentity(destination, sslContext, authenticationConfiguration);
    }

    /**
     * Get a possibly shared, possibly existing connection to the destination.  The authentication and SSL configuration is selected from
     * the currently active authentication context with the given abstract type (if specified).
     *
     * @param destination the destination URI (must not be {@code null})
     * @param abstractType the abstract type of the connection (may be {@code null})
     * @param abstractTypeAuthority the authority name of the abstract type of the connection (may be {@code null})
     * @return the future connection identity (not {@code null})
     */
    default IoFuture<ConnectionPeerIdentity> getConnectedIdentity(URI destination, String abstractType, String abstractTypeAuthority) {
        return getConnectedIdentity(destination, abstractType, abstractTypeAuthority, AuthenticationContext.captureCurrent());
    }

    /**
     * Get a possibly shared, possibly existing connection to the destination.  The authentication and SSL configuration is specified
     * directly.
     *
     * @param destination the destination URI (must not be {@code null})
     * @param sslContext the SSL context to use for secure connections (may be {@code null})
     * @param authenticationConfiguration the authentication configuration to use (must not be {@code null})
     * @return the future connection identity (not {@code null})
     */
    IoFuture<ConnectionPeerIdentity> getConnectedIdentity(URI destination, SSLContext sslContext, AuthenticationConfiguration authenticationConfiguration);

    /**
     * Get a possibly shared, possibly existing connection to the destination, if the connection was already established.
     * The authentication and SSL configuration is specified directly.
     * <p>
     * If no existing connection was found, {@code null} is returned.  If a non-{@code null} {@code IoFuture} is
     * returned, it may represent a complete connection, a failed attempt, or an in-progress attempt.
     *
     * @param destination the destination URI (must not be {@code null})
     * @param abstractType the abstract type of the connection (may be {@code null})
     * @param abstractTypeAuthority the authority name of the abstract type of the connection (may be {@code null})
     * @param context the authentication context to use (must not be {@code null})
     * @return the existing connection, or {@code null} if no connection currently exists
     */
    default IoFuture<ConnectionPeerIdentity> getConnectedIdentityIfExists(URI destination, String abstractType, String abstractTypeAuthority, AuthenticationContext context) {
        Assert.checkNotNullParam("destination", destination);
        Assert.checkNotNullParam("context", context);
        final AuthenticationContextConfigurationClient client = AUTH_CONFIGURATION_CLIENT;
        final SSLContext sslContext;
        try {
            sslContext = client.getSSLContext(destination, context);
        } catch (GeneralSecurityException e) {
            return new FailedIoFuture<>(Messages.conn.failedToConfigureSslContext(e));
        }
        final AuthenticationConfiguration authenticationConfiguration = client.getAuthenticationConfiguration(destination, context, -1, abstractType, abstractTypeAuthority);
        return getConnectedIdentityIfExists(destination, sslContext, authenticationConfiguration);
    }

    /**
     * Get a possibly shared, possibly existing connection to the destination, if the connection was already established.
     * The authentication and SSL configuration is specified directly.
     * <p>
     * If no existing connection was found, {@code null} is returned.  If a non-{@code null} {@code IoFuture} is
     * returned, it may represent a complete connection, a failed attempt, or an in-progress attempt.
     *
     * @param destination the destination URI (must not be {@code null})
     * @param sslContext the SSL context to use for secure connections (may be {@code null})
     * @param authenticationConfiguration the authentication configuration to use (must not be {@code null})
     * @return the existing connection, or {@code null} if no connection currently exists
     */
    IoFuture<ConnectionPeerIdentity> getConnectedIdentityIfExists(URI destination, SSLContext sslContext, AuthenticationConfiguration authenticationConfiguration);

    /**
     * Get a possibly pre-existing connection to the destination.
     *
     * @param destination the destination URI
     * @return the future (or existing) connection
     */
    @Deprecated
    default IoFuture<Connection> getConnection(URI destination) {
        return getConnection(destination, (String) null, null);
    }

    /**
     * Get a possibly pre-existing connection to the destination.  The given abstract type and authority are used
     * to locate the authentication configuration.
     *
     * @param destination the destination URI (must not be {@code null})
     * @param abstractType the abstract type of the connection (may be {@code null})
     * @param abstractTypeAuthority the authority name of the abstract type of the connection (may be {@code null})
     * @return the future (or existing) connection
     */
    @Deprecated
    default IoFuture<Connection> getConnection(URI destination, String abstractType, String abstractTypeAuthority) {
        return new ToConnectionFuture(getConnectedIdentity(destination, abstractType, abstractTypeAuthority));
    }

    /**
     * Get a possibly pre-existing connection to the destination.  The given authentication configuration is used to
     * authenticate the connection.
     * <p>
     * The given SSL context factory is used only for TLS-based protocols.  It may be {@code null}, but in such cases,
     * no TLS-based protocols will be available.
     *
     * @param destination the destination URI (must not be {@code null})
     * @param sslContext the SSL context to use for secure connections (may be {@code null})
     * @param authenticationConfiguration the authentication configuration to use (must not be {@code null})
     * @return the future (or existing) connection
     */
    @Deprecated
    default IoFuture<Connection> getConnection(URI destination, SSLContext sslContext, AuthenticationConfiguration authenticationConfiguration) {
        return new ToConnectionFuture(getConnectedIdentity(destination, sslContext, authenticationConfiguration));
    }

    /**
     * Get a possibly pre-existing connection to the destination.  The connection authentication configuration is used
     * to authenticate
     * the peer if the connection supports multiple identity switching.  The run authentication configuration is used to
     * authenticate
     * the peer if the connection does not support multiple identity switching.
     *
     * @param destination the destination URI (must not be {@code null})
     * @param sslContext the SSL context to use for secure connections (may be {@code null})
     * @param connectionConfiguration the connection authentication configuration (must not be {@code null})
     * @param operateConfiguration the run authentication configuration (must not be {@code null})
     * @return the future (or existing) connection
     */
    @Deprecated
    default IoFuture<Connection> getConnection(URI destination, SSLContext sslContext, AuthenticationConfiguration connectionConfiguration, AuthenticationConfiguration operateConfiguration) {
        return getConnection(destination, sslContext, operateConfiguration);
    }

    /**
     * Get a pre-existing connection to the destination.  The connection authentication configuration is used to
     * authenticate
     * the peer if the connection supports multiple identity switching.  The run authentication configuration is used to
     * authenticate
     * the peer if the connection does not support multiple identity switching.
     * <p>
     * If no existing connection was found, {@code null} is returned.  If a non-{@code null} {@code IoFuture} is
     * returned, it may represent a complete connection, a failed attempt, or an in-progress attempt.
     *
     * @param destination the destination URI (must not be {@code null})
     * @param sslContext the SSL context to use for secure connections (may be {@code null})
     * @param connectionConfiguration the connection authentication configuration (must not be {@code null})
     * @param operateConfiguration the run authentication configuration (must not be {@code null})
     * @return the existing connection, or {@code null} if no connection currently exists
     */
    @Deprecated
    default IoFuture<Connection> getConnectionIfExists(URI destination, SSLContext sslContext, AuthenticationConfiguration connectionConfiguration, AuthenticationConfiguration operateConfiguration) {
        return new ToConnectionFuture(getConnectedIdentityIfExists(destination, sslContext, operateConfiguration));
    }

    /**
     * Get a pre-existing connection to the destination.
     * <p>
     * If no existing connection was found, {@code null} is returned.  If a non-{@code null} {@code IoFuture} is
     * returned, it may represent a complete connection, a failed attempt, or an in-progress attempt.
     *
     * @param destination the destination URI (must not be {@code null})
     * @param abstractType the abstract type of the connection (may be {@code null})
     * @param abstractTypeAuthority the authority name of the abstract type of the connection (may be {@code null})
     * @return the existing connection, or {@code null} if no connection currently exists
     */
    @Deprecated
    default IoFuture<Connection> getConnectionIfExists(URI destination, String abstractType, String abstractTypeAuthority) {
        final AuthenticationContext context = AuthenticationContext.captureCurrent();
        final AuthenticationContextConfigurationClient client = AUTH_CONFIGURATION_CLIENT;
        final SSLContext sslContext;
        try {
            sslContext = client.getSSLContext(destination, context);
        } catch (GeneralSecurityException e) {
            return new FailedIoFuture<>(Messages.conn.failedToConfigureSslContext(e));
        }
        final AuthenticationConfiguration authenticationConfiguration = client.getAuthenticationConfiguration(destination, context, -1, abstractType, abstractTypeAuthority);
        return getConnectionIfExists(destination, sslContext, authenticationConfiguration, authenticationConfiguration);
    }

    /**
     * Get a pre-existing shared connection to the destination.
     * <p>
     * If no existing connection was found, {@code null} is returned.  If a non-{@code null} {@code IoFuture} is
     * returned, it may represent a complete connection, a failed attempt, or an in-progress attempt.
     *
     * @param destination the destination URI (must not be {@code null})
     * @return the existing connection, or {@code null} if no connection currently exists
     */
    @Deprecated
    default IoFuture<Connection> getConnectionIfExists(URI destination) {
        return getConnectionIfExists(destination, null, null);
    }


    /**
     * Open an unshared connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     *
     * @return the future connection
     */
    default IoFuture<Connection> connect(URI destination) {
        return connect(destination, OptionMap.EMPTY);
    }

    /**
     * Open an unshared connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param connectOptions options to configure this connection
     *
     * @return the future connection
     */
    IoFuture<Connection> connect(URI destination, OptionMap connectOptions);

    /**
     * Open an unshared connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param connectOptions options to configure this connection
     * @param authenticationContext the client authentication context to use
     *
     * @return the future connection
     */
    IoFuture<Connection> connect(URI destination, OptionMap connectOptions, AuthenticationContext authenticationContext);

    /**
     * Open an unshared connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param bindAddress the local bind address
     * @param connectOptions options to configure this connection
     * @param authenticationContext the client authentication context to use
     *
     * @return the future connection
     */
    IoFuture<Connection> connect(URI destination, InetSocketAddress bindAddress, OptionMap connectOptions, AuthenticationContext authenticationContext);

    /**
     * Open an unshared connection with a peer.  Returns a future connection which may be used to cancel the connection attempt.
     * This method does not block; use the return value to wait for a result if you wish to block.
     * <p/>
     * You must have the {@link RemotingPermission connect EndpointPermission} to invoke this method.
     *
     * @param destination the destination
     * @param bindAddress the local bind address
     * @param connectOptions options to configure this connection
     * @param sslContext the SSL context to use for secure connections (may be {@code null})
     * @param connectionConfiguration the connection authentication configuration (must not be {@code null})
     * @return the future connection (not {@code null})
     */
    IoFuture<Connection> connect(URI destination, InetSocketAddress bindAddress, OptionMap connectOptions, SSLContext sslContext, AuthenticationConfiguration connectionConfiguration);

    IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final CallbackHandler callbackHandler) throws IOException;

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
