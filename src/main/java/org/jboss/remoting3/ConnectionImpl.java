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

import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.function.UnaryOperator;

import javax.net.ssl.SSLSession;
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.jboss.remoting3._private.IntIndexHashMap;
import org.jboss.remoting3.security.RemotingPermission;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.sasl.WildFlySasl;
import org.wildfly.security.sasl.util.ServerNameSaslServerFactory;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

import static org.jboss.remoting3._private.Messages.log;

class ConnectionImpl extends AbstractHandleableCloseable<Connection> implements Connection {

    private final Attachments attachments = new Attachments();

    private final ConnectionHandler connectionHandler;
    private final Endpoint endpoint;
    private final URI peerUri;
    private final ConnectionPeerIdentityContext peerIdentityContext;
    private final Principal principal;
    private final IntIndexHashMap<Auth> authMap = new IntIndexHashMap<Auth>(Auth::getId);
    private final SaslAuthenticationFactory authenticationFactory;
    private final AuthenticationConfiguration authenticationConfiguration;

    ConnectionImpl(final EndpointImpl endpoint, final ConnectionHandlerFactory connectionHandlerFactory, final ConnectionProviderContext connectionProviderContext, final URI peerUri, final Principal principal, final UnaryOperator<SaslClientFactory> saslClientFactoryOperator, final SaslAuthenticationFactory authenticationFactory, final AuthenticationConfiguration authenticationConfiguration) {
        super(endpoint.getExecutor(), true);
        this.endpoint = endpoint;
        this.peerUri = peerUri;
        this.principal = principal;
        this.authenticationConfiguration = authenticationConfiguration;
        this.connectionHandler = connectionHandlerFactory.createInstance(endpoint.new LocalConnectionContext(connectionProviderContext, this));
        this.authenticationFactory = authenticationFactory;
        this.peerIdentityContext = new ConnectionPeerIdentityContext(this, connectionHandler.getOfferedMechanisms());
    }

    protected void closeAction() throws IOException {
        connectionHandler.closeAsync();
        connectionHandler.addCloseHandler((closed, exception) -> closeComplete());
        for (Auth auth : authMap) {
            auth.dispose();
        }
        final ConnectionPeerIdentityContext peerIdentityContext = this.peerIdentityContext;
        if (peerIdentityContext != null) peerIdentityContext.connectionClosed();
    }

    public SocketAddress getLocalAddress() {
        return connectionHandler.getLocalAddress();
    }

    public SocketAddress getPeerAddress() {
        return connectionHandler.getPeerAddress();
    }

    AuthenticationConfiguration getAuthenticationConfiguration() {
        return authenticationConfiguration;
    }

    ConnectionHandler getConnectionHandler() {
        return connectionHandler;
    }

    @Override
    public SSLSession getSslSession() {
        return connectionHandler.getSslSession();
    }

    public IoFuture<Channel> openChannel(final String serviceType, final OptionMap optionMap) {
        FutureResult<Channel> result = new FutureResult<Channel>(getExecutor());
        result.addCancelHandler(connectionHandler.open(serviceType, result, optionMap));
        return result.getIoFuture();
    }

    public String getRemoteEndpointName() {
        return connectionHandler.getRemoteEndpointName();
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public URI getPeerURI() {
        return peerUri;
    }

    public SecurityIdentity getLocalIdentity() {
        return connectionHandler.getLocalIdentity();
    }

    public SecurityIdentity getLocalIdentity(final int id) {
        if (id == 0) {
            final SaslAuthenticationFactory authenticationFactory = this.authenticationFactory;
            return authenticationFactory == null ? null : authenticationFactory.getSecurityDomain().getAnonymousSecurityIdentity();
        } else if (id == 1) {
            return getLocalIdentity();
        }
        final Auth auth = authMap.get(id);
        return auth != null ? (SecurityIdentity) auth.getSaslServer().getNegotiatedProperty(WildFlySasl.SECURITY_IDENTITY) : null;
    }

    public int getPeerIdentityId() {
        return getPeerIdentityContext().getCurrentIdentity().getIndex();
    }

    public Attachments getAttachments() {
        return attachments;
    }

    public String toString() {
        return String.format("Remoting connection <%x> on %s", Integer.valueOf(hashCode()), endpoint);
    }

    public ConnectionPeerIdentity getConnectionPeerIdentity() throws SecurityException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(RemotingPermission.GET_CONNECTION_PEER_IDENTITY);
        }
        return getPeerIdentityContext().getConnectionIdentity();
    }

    public ConnectionPeerIdentity getConnectionAnonymousIdentity() {
        return getPeerIdentityContext().getAnonymousIdentity();
    }

    public ConnectionPeerIdentityContext getPeerIdentityContext() {
        final ConnectionPeerIdentityContext peerIdentityContext = this.peerIdentityContext;
        if (peerIdentityContext == null) {
            throw Assert.unsupported();
        }
        return peerIdentityContext;
    }

    @Override
    public boolean supportsRemoteAuth() {
        return connectionHandler.supportsRemoteAuth();
    }

    public Principal getPrincipal() {
        return principal;
    }

    public void receiveAuthRequest(final int id, final String mechName, final byte[] initialResponse) {
        log.tracef("Received authentication request for ID %08x, mech %s", id, mechName);
        if (id == 0 || id == 1) {
            // ignore
            return;
        }
        getExecutor().execute(() -> {
            final SaslServer saslServer;
            final IntIndexHashMap<Auth> authMap = this.authMap;
            try {
                saslServer = authenticationFactory.createMechanism(mechName, f ->
                    new ServerNameSaslServerFactory(f, endpoint.getName())
                );
            } catch (SaslException e) {
                log.trace("Authentication failed at mechanism creation", e);
                try {
                    Auth oldAuth = authMap.put(new Auth(id, new RejectingSaslServer()));
                    if (oldAuth != null) oldAuth.dispose();
                    connectionHandler.sendAuthReject(id);
                } catch (IOException e1) {
                    log.trace("Failed to send auth reject", e1);
                }
                return;
            }
            // clear out any old auth
            final Auth auth = new Auth(id, saslServer);
            Auth oldAuth = authMap.put(auth);
            if (oldAuth != null) oldAuth.dispose();
            final byte[] challenge;
            try {
                challenge = saslServer.evaluateResponse(initialResponse);
            } catch (SaslException e) {
                log.trace("Authentication failed at response evaluation", e);
                try {
                    connectionHandler.sendAuthReject(id);
                } catch (IOException e1) {
                    authMap.remove(auth);
                    auth.dispose();
                    log.trace("Failed to send auth reject", e1);
                }
                return;
            }
            if (saslServer.isComplete()) {
                try {
                    connectionHandler.sendAuthSuccess(id, challenge);
                } catch (IOException e) {
                    authMap.remove(auth);
                    auth.dispose();
                    log.trace("Failed to send auth success", e);
                }
                return;
            } else {
                try {
                    connectionHandler.sendAuthChallenge(id, challenge);
                } catch (IOException e) {
                    authMap.remove(auth);
                    auth.dispose();
                    log.trace("Failed to send auth challenge", e);
                }
                return;
            }
        });
    }

    void receiveAuthResponse(final int id, final byte[] response) {
        log.tracef("Received authentication response for ID %08x", id);
        if (id == 0 || id == 1) {
            // ignore
            return;
        }
        getExecutor().execute(() -> {
            Auth auth = authMap.get(id);
            if (auth == null) {
                auth = authMap.putIfAbsent(new Auth(id, new RejectingSaslServer()));
                if (auth == null) {
                    // reject
                    try {
                        connectionHandler.sendAuthReject(id);
                    } catch (IOException e1) {
                        log.trace("Failed to send auth reject", e1);
                    }
                    return;
                }
            }
            final SaslServer saslServer = auth.getSaslServer();
            final byte[] challenge;
            try {
                challenge = saslServer.evaluateResponse(response);
            } catch (SaslException e) {
                try {
                    connectionHandler.sendAuthReject(id);
                } catch (IOException e1) {
                    authMap.remove(auth);
                    auth.dispose();
                    log.trace("Failed to send auth reject", e1);
                }
                return;
            }
            if (saslServer.isComplete()) {
                try {
                    connectionHandler.sendAuthSuccess(id, challenge);
                } catch (IOException e) {
                    authMap.remove(auth);
                    auth.dispose();
                    log.trace("Failed to send auth success", e);
                }
                return;
            } else {
                try {
                    connectionHandler.sendAuthChallenge(id, challenge);
                } catch (IOException e) {
                    authMap.remove(auth);
                    auth.dispose();
                    log.trace("Failed to send auth challenge", e);
                }
                return;
            }
        });
    }

    void receiveAuthDelete(final int id) {
        log.tracef("Received authentication delete for ID %08x", id);
        if (id == 0 || id == 1) {
            // ignore
            return;
        }
        getExecutor().execute(() -> {
            final Auth auth = authMap.removeKey(id);
            if (auth != null) auth.dispose();
            log.tracef("Deleted authentication ID %08x", id);
        });
    }

    static final class Auth {
        private final int id;
        private final SaslServer saslServer;

        Auth(final int id, final SaslServer saslServer) {
            this.id = id;
            this.saslServer = saslServer;
        }

        int getId() {
            return id;
        }

        SaslServer getSaslServer() {
            return saslServer;
        }

        void dispose() {
            try {
                saslServer.dispose();
            } catch (SaslException se) {
                log.trace("Failed to dispose SASL mechanism", se);
            }
        }
    }
}
