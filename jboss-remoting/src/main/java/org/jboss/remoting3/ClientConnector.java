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

import org.jboss.xnio.IoFuture;

/**
 * A client connector.  Such a connector can usually be sent across a link to a specific peer.  Attempting to access
 * the client from the wrong peer, or attempting to send the connector to a peer for whom it is not intended, will
 * result in an exception.
 */
public interface ClientConnector<I, O> {

    /**
     * Get the future client associated with this connector.  This method may only be called after this connector
     * has passed over its associated connection.
     *
     * @return the future client
     * @throws SecurityException if this client is being accessed from the wrong peer
     */
    IoFuture<? extends Client<I, O>> getFutureClient() throws SecurityException;

    /**
     * Get the future client associated with this connector.  This method may only be called after this connector
     * has passed over its associated connection.
     *
     * @param classloader the explicit classloader to use for unmarshalling replies
     * @return the future client
     * @throws SecurityException if this client is being accessed from the wrong peer
     */
    IoFuture<? extends Client<I, O>> getFutureClient(ClassLoader classloader) throws SecurityException;

    /**
     * Get the client context associated with this connector.  This method may only be called from the originating
     * side of the connection.
     *
     * @return the client context
     * @throws SecurityException if the client context is accessed on the remote side of the connection
     */
    ClientContext getClientContext() throws SecurityException;
}
