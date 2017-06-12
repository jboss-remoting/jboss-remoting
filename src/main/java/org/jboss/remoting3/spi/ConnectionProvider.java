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

package org.jboss.remoting3.spi;

import java.net.SocketAddress;
import java.net.URI;
import java.util.Collection;
import java.util.function.UnaryOperator;

import org.jboss.remoting3.HandleableCloseable;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.xnio.Cancellable;
import org.xnio.OptionMap;
import org.xnio.Result;

import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslClientFactory;

/**
 * A connection provider.  Used to establish connections with remote systems.  There is typically one instance
 * of this interface per connection provider factory per endpoint.
 */
public interface ConnectionProvider extends HandleableCloseable<ConnectionProvider> {

    /**
     * Open an outbound connection, using the (optionally) given socket addresses as source and destination.
     * This method is expected to be non-blocking, with the result stored in the result variable possibly asynchronously.
     *
     * @param destination the destination URI, or {@code null} if none is given
     * @param bindAddress the address to bind to, or {@code null} if none is given
     * @param connectOptions the options to use for this connection
     * @param result the result which should receive the connection
     * @param authenticationConfiguration the configuration to use for authentication of the connection
     * @param sslContext the SSL context to use
     * @param saslClientFactoryOperator A unary operator to apply to the SaslClientFactory used.
     * @param serverMechs the list of server mechanism names to advertise to the peer (may be empty; not {@code null})
     * @return a handle which may be used to cancel the connect attempt
     * @throws IllegalArgumentException if any of the given arguments are not valid for this protocol
     */
    Cancellable connect(URI destination, SocketAddress bindAddress, OptionMap connectOptions, Result<ConnectionHandlerFactory> result, AuthenticationConfiguration authenticationConfiguration, SSLContext sslContext, UnaryOperator<SaslClientFactory> saslClientFactoryOperator, final Collection<String> serverMechs);

    /**
     * Get the user data associated with this connection provider.  This object should implement all of the
     * provider interfaces which are supported by this provider.  Must not return {@code null}.
     *
     * @return the user data (not {@code null})
     * @see NetworkServerProvider
     */
    Object getProviderInterface();

    /**
     * The object to use when a connection provider has no provider interfaces.
     */
    Object NO_PROVIDER_INTERFACES = new Object();

}
