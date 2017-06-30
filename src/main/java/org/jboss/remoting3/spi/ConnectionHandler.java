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

import java.io.IOException;
import java.net.SocketAddress;
import java.security.Principal;
import java.util.Set;

import javax.net.ssl.SSLSession;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.HandleableCloseable;
import org.wildfly.security.auth.server.SecurityIdentity;
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

    /**
     * Get the SASL server name that the peer gives for itself.
     *
     * @return the SASL server name that the peer gives for itself (must not be {@code null})
     */
    String getPeerSaslServerName();

    /**
     * Get the local SASL server name that we have given to the peer.
     *
     * @return the local SASL server name that we have given to the peer (must not be {@code null})
     */
    String getLocalSaslServerName();

    /**
     * Get the local identity corresponding to the peer authentication which was performed on this connection, if it
     * is an incoming connection.  Outbound connections may return {@code null} for this property.
     *
     * @return the local identity of a connection, or {@code null} if the connection has no local identity and no
     * local security domain configuration
     */
    SecurityIdentity getLocalIdentity();

    /**
     * Determine if the connection handler supports the remote authentication protocol.
     *
     * @return {@code true} if remote authentication is supported, {@code false} otherwise
     */
    boolean supportsRemoteAuth();

    /**
     * Get the available SASL mechanisms.
     *
     * @return the available SASL mechanisms
     */
    Set<String> getOfferedMechanisms();

    /**
     * Get the principal used to authenticate the local client against the peer.
     *
     * @return the local client principal, or {@code null} if the connection is inbound
     */
    Principal getPrincipal();

    /**
     * Send an authentication request.
     *
     * @param id the ID number to use
     * @param mechName the mechanism name (not {@code null})
     * @param initialResponse the initial response (possibly {@code null})
     * @throws IOException if a transmission error occurs
     */
    void sendAuthRequest(int id, String mechName, byte[] initialResponse) throws IOException;

    /**
     * Send an authentication challenge.
     *
     * @param id the ID number to use
     * @param challenge the challenge body (not {@code null})
     * @throws IOException if a transmission error occurs
     */
    void sendAuthChallenge(int id, byte[] challenge) throws IOException;

    /**
     * Send an authentication response.
     *
     * @param id the ID number to use
     * @param response the response body (not {@code null})
     * @throws IOException if a transmission error occurs
     */
    void sendAuthResponse(int id, byte[] response) throws IOException;

    /**
     * Send an authentication complete message.
     *
     * @param id the ID number to use
     * @param challenge the final challenge (may be {@code null} if none is needed)
     * @throws IOException if a transmission error occurs
     */
    void sendAuthSuccess(int id, byte[] challenge) throws IOException;

    /**
     * Send an authentication reject message.
     *
     * @param id the ID number to use
     * @throws IOException if a transmission error occurs
     */
    void sendAuthReject(int id) throws IOException;

    /**
     * Send an authentication delete message.
     *
     * @param id the ID number to use
     * @throws IOException if a transmission error occurs
     */
    void sendAuthDelete(int id) throws IOException;

    /**
     * Send an authentication delete acknowledgement message.
     *
     * @param id the ID number to use
     * @throws IOException if a transmission error occurs
     */
    void sendAuthDeleteAck(int id) throws IOException;
}
