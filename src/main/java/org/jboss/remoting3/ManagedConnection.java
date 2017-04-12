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

package org.jboss.remoting3;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.security.Principal;

import javax.net.ssl.SSLSession;

import org.wildfly.security.auth.AuthenticationException;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ManagedConnection implements Connection {
    private final Connection delegate;
    private final ConnectionInfo connectionInfo;
    private final AuthenticationConfiguration authConfig;
    private final FutureResult<Connection> futureResult;

    ManagedConnection(final Connection delegate, final ConnectionInfo connectionInfo, final AuthenticationConfiguration authConfig, final FutureResult<Connection> futureResult) {
        this.delegate = delegate;
        this.connectionInfo = connectionInfo;
        this.authConfig = authConfig;
        this.futureResult = futureResult;
        delegate.addCloseHandler((c, e) -> connectionInfo.connectionClosed(authConfig, futureResult));
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

    public String getProtocol() {
        return delegate.getProtocol();
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
        connectionInfo.connectionClosed(authConfig, futureResult);
        delegate.close();
    }

    public void awaitClosed() throws InterruptedException {
        delegate.awaitClosed();
    }

    public void awaitClosedUninterruptibly() {
        delegate.awaitClosedUninterruptibly();
    }

    public void closeAsync() {
        connectionInfo.connectionClosed(authConfig, futureResult);
        delegate.closeAsync();
    }

    public Key addCloseHandler(final CloseHandler<? super Connection> handler) {
        return delegate.addCloseHandler(handler);
    }

    public boolean isOpen() {
        return delegate.isOpen();
    }

    public Attachments getAttachments() {
        return delegate.getAttachments();
    }

    public ConnectionPeerIdentity getConnectionPeerIdentity() throws SecurityException {
        return delegate.getConnectionPeerIdentity();
    }

    public ConnectionPeerIdentity getConnectionAnonymousIdentity() {
        return delegate.getConnectionAnonymousIdentity();
    }

    public ConnectionPeerIdentityContext getPeerIdentityContext() {
        return delegate.getPeerIdentityContext();
    }

    public Principal getPrincipal() {
        return delegate.getPrincipal();
    }

    public boolean supportsRemoteAuth() {
        return delegate.supportsRemoteAuth();
    }
}
