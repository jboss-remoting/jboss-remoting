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

import java.net.SocketAddress;
import java.net.URI;

import javax.net.ssl.SSLSession;

import org.jboss.remoting3.security.RemotingPermission;
import org.wildfly.security.auth.AuthenticationException;
import org.wildfly.security.auth.client.PeerIdentity;
import org.wildfly.security.auth.client.PeerIdentityContext;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * A connection to a remote peer.
 */
public interface Connection extends HandleableCloseable<Connection>, Attachable {

    /**
     * Get the local address of this connection, if any.
     *
     * @return the local address of this connection, or {@code null} if there is no local address
     */
    SocketAddress getLocalAddress();

    /**
     * Get the local address of this connection, cast to a specific type, if any.  If there is an address but it
     * cannot be cast to the given type, {@code null} is returned.
     *
     * @param type the address type class
     * @param <S> the address type
     * @return the local address of this connection, or {@code null} if there is no local address or the address is
     *      of the wrong type
     */
    default <S extends SocketAddress> S getLocalAddress(Class<S> type) {
        final SocketAddress localAddress = getLocalAddress();
        return type.isInstance(localAddress) ? type.cast(localAddress) : null;
    }

    /**
     * Get the peer address of this connection, if any.
     *
     * @return the peer address of this connection, or {@code null} if there is no peer address
     */
    SocketAddress getPeerAddress();

    /**
     * Get the peer address of this connection, cast to a specific type, if any.  If there is an address but it
     * cannot be cast to the given type, {@code null} is returned.
     *
     * @param type the address type class
     * @param <S> the address type
     * @return the peer address of this connection, or {@code null} if there is no peer address or the address is
     *      of the wrong type
     */
    default <S extends SocketAddress> S getPeerAddress(Class<S> type) {
        final SocketAddress peerAddress = getPeerAddress();
        return type.isInstance(peerAddress) ? type.cast(peerAddress) : null;
    }

    /**
     * Get the underlying {@link SSLSession} for this connection if one is established.
     *
     * @return the {@link SSLSession} for the connection if one is established, otherwise returns {@code null}.
     */
    SSLSession getSslSession();

    /**
     * Open a channel to a remote service on this connection.
     *
     * @param serviceType the service type
     * @param optionMap the option map
     * @return the future channel
     */
    IoFuture<Channel> openChannel(String serviceType, OptionMap optionMap);

    /**
     * Get the name of the remote endpoint, if it has one.
     *
     * @return the remote endpoint name or {@code null} if it is anonymous
     */
    String getRemoteEndpointName();

    /**
     * Get the local endpoint.
     *
     * @return the local endpoint
     */
    Endpoint getEndpoint();

    /**
     * Get the URI of the remote peer.  The URI may be constructed or {@code null} if the connection was accepted rather than established.
     *
     * @return the peer URI, or {@code null} if none is available
     */
    URI getPeerURI();

    /**
     * Get the local identity of this inbound connection.
     *
     * @return the local identity, or {@code null} if the connection is outbound
     */
    SecurityIdentity getLocalIdentity();

    /**
     * Get the local identity associated with the given ID that was previously shared to the peer.
     *
     * @param id the numeric ID
     * @return the identity, or {@code null} if the the given ID is not valid
     */
    SecurityIdentity getLocalIdentity(int id);

    /**
     * The the ID number for the peer identity which is currently associated with the calling thread.  The special
     * value 0 indicates that the connection's identity is in use; the special value 1 indicates that the anonymous
     * identity is in use.
     *
     * @return the numeric ID
     * @throws AuthenticationException if an authentication was required to get the ID, but the authentication failed
     */
    int getPeerIdentityId() throws AuthenticationException;

    /**
     * Get the peer identity for the connection.  This is the identity that corresponds to the connection's own authentication
     * result.
     *
     * @return the peer identity for the connection
     *
     * @throws SecurityException if a security manager is installed and the caller is not granted the {@code getConnectionPeerIdentity} {@link RemotingPermission}
     */
    PeerIdentity getConnectionPeerIdentity() throws SecurityException;

    /**
     * Get the anonymous peer identity for the connection.  When this identity is in force, the peer will use its local
     * anonymous identity.
     *
     * @return the anonymous peer identity for the connection
     */
    PeerIdentity getConnectionAnonymousIdentity();

    /**
     * Get the peer identity context for the connection.  This context can be used to authenticate additional identities
     * to the peer which can then be propagated to that peer.  If the connection is managed, the lifespan of the context
     * may encompass several connection lifespans when the managed connection is broken and subsequently re-established.
     *
     * @return the peer identity context
     */
    PeerIdentityContext getPeerIdentityContext();
}
