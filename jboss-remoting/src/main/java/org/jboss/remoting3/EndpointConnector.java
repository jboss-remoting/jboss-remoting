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
 * An endpoint connector.  Used to connect a whole endpoint to another whole endpoint.  Typically, services are then
 * shared between the endpoints in some fashion, though this need not be the case.
 */
public interface EndpointConnector extends HandleableCloseable<EndpointConnector> {

    /**
     * Connect the given endpoint to the remote URI.
     *
     * @param endpoint the endpoint to connect
     * @param connectUri the connection URI
     * @return the future handle, which may be used to terminate the connection
     */
    IoFuture<? extends HandleableCloseable> connect(Endpoint endpoint, URI connectUri);
}
