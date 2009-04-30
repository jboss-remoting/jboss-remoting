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

import org.jboss.xnio.IoFuture;
import java.io.IOException;

/**
 * A connection to a remote peer.
 * <p/>
 * This interface is part of the Remoting public API.  It is intended to be consumed by Remoting applications; it is
 * not intended to be implemented by them.  Methods may be added to this interface in future minor releases without
 * advance notice.
 */
public interface Connection extends HandleableCloseable<Connection> {

    /**
     * Open a client on the remote side of this connection.
     *
     * @param serviceType the service type
     * @param groupName the group name
     * @param requestClass the request class
     * @param replyClass the reply class
     * @param <I> the request type
     * @param <O> the reply type
     * @return the future client
     */
    <I, O> IoFuture<? extends Client<I, O>> openClient(String serviceType, String groupName, Class<I> requestClass, Class<O> replyClass);

    /**
     * Create a client connector which may <b>only</b> transmitted to the remote side of this connection, allowing
     * it to use the included service.
     *
     * @param listener the local listener
     * @param requestClass the request class
     * @param replyClass the reply class
     * @param <I> the request type
     * @param <O> the reply type
     * @return a connector which may be sent to the connection peer
     */
    <I, O> ClientConnector<I, O> createClientConnector(RequestListener<I, O> listener, Class<I> requestClass, Class<O> replyClass) throws IOException;
}
