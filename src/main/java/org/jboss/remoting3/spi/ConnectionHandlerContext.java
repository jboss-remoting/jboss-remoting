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
