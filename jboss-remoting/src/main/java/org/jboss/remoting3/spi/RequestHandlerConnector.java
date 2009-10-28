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

import org.jboss.xnio.Cancellable;

/**
 * A holder for a request handler that is to be sent to a remote peer.
 */
public interface RequestHandlerConnector {

    /**
     * Get the request handler.  If this connector was forwarded, this method may only be called once;
     * further attempts to call it should result in a {@code SecurityException}.
     *
     * @param result the result of the connection
     * @return the cancellation handle
     * @throws SecurityException if this is a forwarding connector, thrown if the connector was not forwarded or if this method is called more than one time
     */
    Cancellable createRequestHandler(org.jboss.xnio.Result<RequestHandler> result) throws SecurityException;
}
