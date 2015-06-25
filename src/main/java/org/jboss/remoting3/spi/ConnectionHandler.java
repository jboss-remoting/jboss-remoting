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

import javax.net.ssl.SSLSession;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.HandleableCloseable;
import org.xnio.Cancellable;
import org.xnio.OptionMap;
import org.xnio.Result;

/**
 * A connection to a foreign endpoint.  This interface is implemented by the protocol implementation.
 */
public interface ConnectionHandler extends HandleableCloseable<ConnectionHandler> {

    /**
     * Open a request handler.
     *
     * @param serviceType the service type string
     * @param result the result for the connected channel
     * @param optionMap the options for this service
     * @return a handle which may be used to cancel the pending operation
     */
    Cancellable open(String serviceType, Result<Channel> result, OptionMap optionMap);

    /**
    /**
     * Get the underlying {@link SSLSession} for this connection if one is established.
     *
     * @return the {@link SSLSession} for the connection if one is established, otherwise returns {@code null}.
     */
    SSLSession getSslSession();

    /**
     * Get the name of the remote endpoint.
     *
     * @return the remote endpoint name
     */
    String getRemoteEndpointName();

    /**
     * Get the local address, if any.
     *
     * @return the local address, or {@code null} if there is none
     */
    SocketAddress getLocalAddress();

    /**
     * Get the peer address, if any.
     *
     * @return the peer address, or {@code null} if there is none
     */
    SocketAddress getPeerAddress();
}
