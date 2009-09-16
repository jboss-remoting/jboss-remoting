/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

import java.net.URI;
import org.jboss.remoting3.OptionMap;

/**
 * A connection provider.  Used to establish connections with remote systems.  There is typically one instance
 * of this interface per connection provider factory per endpoint.
 *
 * @remoting.implement
 */
public interface ConnectionProvider<T> {

    /**
     * Open an outbound connection to the given URI.  This method is expected to be non-blocking, with the result
     * stored in the result variable possibly asynchronously.
     *
     * @param uri the URI to connect to
     * @param connectOptions the options to use for this connection
     * @param result the result which should receive the connection
     * @return a handle which may be used to cancel the connect attempt
     * @throws IllegalArgumentException if the URI is not valid
     */
    Cancellable connect(URI uri, OptionMap connectOptions, Result<ConnectionHandlerFactory> result) throws IllegalArgumentException;

    /**
     * Get the user data associated with this connection provider.
     *
     * @return the user data
     */
    T getProviderInterface();
}
