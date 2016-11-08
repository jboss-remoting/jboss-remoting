/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.security.Principal;

import javax.net.ssl.SSLSession;

import org.wildfly.security.auth.AuthenticationException;
import org.wildfly.security.auth.client.PeerIdentity;
import org.wildfly.security.auth.client.PeerIdentityContext;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ManagedConnection implements Connection {
    private final Connection delegate;
    private final FutureConnection futureConnection;
    private final FutureResult<Connection> futureResult;

    ManagedConnection(final Connection delegate, final FutureConnection futureConnection, final FutureResult<Connection> futureResult) {
        this.delegate = delegate;
        this.futureConnection = futureConnection;
        this.futureResult = futureResult;
    }

    public SocketAddress getLocalAddress() {
        return delegate.getLocalAddress();
    }

    public <S extends SocketAddress> S getLocalAddress(final Class<S> type) {
        return delegate.getLocalAddress(type);
    }

    public SocketAddress getPeerAddress() {
        return delegate.getPeerAddress();
    }

    public <S extends SocketAddress> S getPeerAddress(final Class<S> type) {
        return delegate.getPeerAddress(type);
    }

    public SSLSession getSslSession() {
        return delegate.getSslSession();
    }

    public IoFuture<Channel> openChannel(final String serviceType, final OptionMap optionMap) {
        return delegate.openChannel(serviceType, optionMap);
    }

    public String getRemoteEndpointName() {
        return delegate.getRemoteEndpointName();
    }

    public Endpoint getEndpoint() {
        return delegate.getEndpoint();
    }

    public URI getPeerURI() {
        return delegate.getPeerURI();
    }

    public SecurityIdentity getLocalIdentity() {
        return delegate.getLocalIdentity();
    }

    public SecurityIdentity getLocalIdentity(final int id) {
        return delegate.getLocalIdentity(id);
    }

    public int getPeerIdentityId() throws AuthenticationException {
        return delegate.getPeerIdentityId();
    }

    public void close() throws IOException {
        futureConnection.clearRef(futureResult);
        delegate.close();
    }

    public void awaitClosed() throws InterruptedException {
        delegate.awaitClosed();
    }

    public void awaitClosedUninterruptibly() {
        delegate.awaitClosedUninterruptibly();
    }

    public void closeAsync() {
        futureConnection.clearRef(futureResult);
        delegate.closeAsync();
    }

    public Key addCloseHandler(final CloseHandler<? super Connection> handler) {
        return delegate.addCloseHandler(handler);
    }

    public Attachments getAttachments() {
        return delegate.getAttachments();
    }

    public PeerIdentity getConnectionPeerIdentity() throws SecurityException {
        return delegate.getConnectionPeerIdentity();
    }

    public PeerIdentity getConnectionAnonymousIdentity() {
        return delegate.getConnectionAnonymousIdentity();
    }

    public PeerIdentityContext getPeerIdentityContext() {
        return delegate.getPeerIdentityContext();
    }

    public Principal getPrincipal() {
        return delegate.getPrincipal();
    }
}
