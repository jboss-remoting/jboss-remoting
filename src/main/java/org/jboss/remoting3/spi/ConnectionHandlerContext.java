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

import org.jboss.remoting3.Connection;

/**
 * The context for connection handlers.  Used to inform the endpoint of incoming events on an established connection.
 */
public interface ConnectionHandlerContext {

    /**
     * Get the connection provider context associated with this connection handler context.
     *
     * @return the connection provider context
     */
    ConnectionProviderContext getConnectionProviderContext();

    /**
     * Get a registered service.  This method will return immediately.
     *
     * @param serviceType the service type string
     * @return the service information, or {@code null} if no such service is currently registered
     */
    RegisteredService getRegisteredService(String serviceType);

    /**
     * Indicate that the remote side has terminated the connection, so the local side should be closed as well.
     */
    void remoteClosed();

    /**
     * Get the connection corresponding to this connection handler context.
     *
     * @return the connection
     */
    Connection getConnection();

    /**
     * Receive an authentication request.
     *
     * @param id the ID number to use
     * @param mechName the mechanism name (not {@code null})
     * @param initialResponse the initial response (possibly {@code null})
     */
    void receiveAuthRequest(int id, String mechName, byte[] initialResponse);

    /**
     * Receive an authentication challenge.
     *
     * @param id the ID number to use
     * @param challenge the challenge body (not {@code null})
     */
    void receiveAuthChallenge(int id, byte[] challenge);

    /**
     * Receive an authentication response.
     *
     * @param id the ID number to use
     * @param response the response body (not {@code null})
     */
    void receiveAuthResponse(int id, byte[] response);

    /**
     * Receive an authentication complete message.
     *
     * @param id the ID number to use
     * @param challenge the final challenge (may be {@code null} if none is needed)
     */
    void receiveAuthSuccess(int id, byte[] challenge);

    /**
     * Receive an authentication reject message.
     *
     * @param id the ID number to use
     */
    void receiveAuthReject(int id);

    /**
     * Receive an authentication delete message.
     *
     * @param id the ID number to use
     */
    void receiveAuthDelete(int id);

    /**
     * Receive an authentication delete acknowledgement message.
     *
     * @param id the ID number to use
     */
    void receiveAuthDeleteAck(int id);
}
