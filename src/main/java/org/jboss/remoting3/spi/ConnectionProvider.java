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

package org.jboss.remoting3.spi;

import java.net.SocketAddress;
import java.net.URI;

import org.jboss.remoting3.HandleableCloseable;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.xnio.Cancellable;
import org.xnio.OptionMap;
import org.xnio.Result;

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
     * @param saslClientFactory the SASL client factory to use for authentication mechanisms
     * @return a handle which may be used to cancel the connect attempt
     * @throws IllegalArgumentException if any of the given arguments are not valid for this protocol
     */
    Cancellable connect(URI destination, SocketAddress bindAddress, OptionMap connectOptions, Result<ConnectionHandlerFactory> result, AuthenticationConfiguration authenticationConfiguration, SaslClientFactory saslClientFactory);

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
