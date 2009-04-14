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

package org.jboss.remoting3;

import java.net.URI;
import org.jboss.xnio.IoFuture;

/**
 * A client source connector.  Opens a connection to a URI which provides a {@code ClientSource} instance.  Instances of this
 * interface may only be able to support a single URI scheme.  Depending on the implementation, the URI may be a
 * protocol URI or a service URI.
 */
public interface ClientSourceConnector extends HandleableCloseable<ClientSourceConnector> {

    /**
     * Establish a client source connection.
     *
     * @param requestType the request class
     * @param replyType the reply class
     * @param connectUri the URI to connect to
     * @param <I> the request type
     * @param <O> the reply type
     * @return the future client
     * @throws IllegalArgumentException if the provided URI scheme is not supported by this connector
     */
    <I, O> IoFuture<? extends ClientSource<I, O>> openClientSource(Class<I> requestType, Class<O> replyType, URI connectUri) throws IllegalArgumentException;
}