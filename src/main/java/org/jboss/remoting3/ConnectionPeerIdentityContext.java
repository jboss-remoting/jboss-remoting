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

import static java.security.AccessController.doPrivileged;
import static org.jboss.remoting3._private.Messages.log;

import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

import org.jboss.remoting3._private.IntIndexHashMap;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.AuthenticationException;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.client.PeerIdentityContext;
import org.wildfly.security.auth.principal.AnonymousPrincipal;

/**
 * A peer identity context for a connection which supports remote authentication-based identity multiplexing.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConnectionPeerIdentityContext extends PeerIdentityContext {

    private static final byte[] NO_BYTES = new byte[0];
    private final ConnectionImpl connection;
    private final Collection<String> offeredMechanisms;
    private final ConnectionPeerIdentity anonymousIdentity;
    private final ConnectionPeerIdentity connectionIdentity;
    private final IntIndexHashMap<Authentication> authMap = new IntIndexHashMap<Authentication>(Authentication::getId);

    private static final AuthenticationContextConfigurationClient CLIENT = doPrivileged((PrivilegedAction<AuthenticationContextConfigurationClient>) AuthenticationContextConfigurationClient::new);

    ConnectionPeerIdentityContext(final ConnectionImpl connection, final Collection<String> offeredMechanisms) {
        this.connection = connection;
        this.offeredMechanisms = offeredMechanisms == null ? Collections.emptySet() : offeredMechanisms;
        anonymousIdentity = constructIdentity(conf -> new ConnectionPeerIdentity(conf, AnonymousPrincipal.getInstance(), 0, connection));
        connectionIdentity = constructIdentity(conf -> new ConnectionPeerIdentity(conf, connection.getPrincipal(), 1, connection));
    }

    /**
     * Perform an authentication.
     *
     * @param configuration the authentication configuration to use (must not be {@code null})
     * @return the peer identity (not {@code null})
     * @throws AuthenticationException if the authentication attempt failed
     */
    public ConnectionPeerIdentity authenticate(final AuthenticationConfiguration configuration) throws AuthenticationException {
        Assert.checkNotNullParam("configuration", configuration);
        final ConnectionImpl connection = this.connection;
        if (configuration.equals(connection.getAuthenticationConfiguration())) {
            return connectionIdentity;
        }
        if (! connection.supportsRemoteAuth()) {
            throw log.authenticationNotSupported();
        }
        final AuthenticationContextConfigurationClient client = CLIENT;
        Authentication authentication;
        final IntIndexHashMap<Authentication> authMap = this.authMap;
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        int id;
        do {
            id = random.nextInt();
        } while (id == 0 || id == 1 || authMap.containsKey(id) || authMap.putIfAbsent(authentication = new Authentication(id)) != null);
        final int finalId = id;
        SaslClient saslClient;
        boolean intr = Thread.currentThread().isInterrupted();
        if (intr) {
            throw log.authenticationInterrupted();
        }
        try {
            final Principal principal = client.getPrincipal(configuration);
            final ConnectionHandler connectionHandler = connection.getConnectionHandler();
            // try each mech in turn, unless the peer explicitly rejects
            Set<String> mechanisms = new LinkedHashSet<>(offeredMechanisms);
            while (! mechanisms.isEmpty()) {
                try {
                    saslClient = client.createSaslClient(connection.getPeerURI(), configuration, mechanisms);
                } catch (SaslException e) {
                    throw log.authenticationNoSaslClient(e);
                }
                if (saslClient == null) {
                    // break out to "no mechs left" error
                    break;
                }
                byte[] response;
                try  {
                    if (saslClient.hasInitialResponse()) {
                        response = saslClient.evaluateChallenge(NO_BYTES);
                    } else {
                        response = null;
                    }
                    connectionHandler.sendAuthRequest(id, saslClient.getMechanismName(), response);
                } catch (IOException e) {
                    // including SaslException
                    authMap.remove(authentication);
                    safeDispose(saslClient);
                    throw log.authenticationExceptionIo(e);
                }
                // the main loop
                byte[] challenge;
                int status;
                for (;;) {
                    synchronized (authentication) {
                        status = authentication.getStatus();
                        while (status == WAITING) {
                            try {
                                authentication.wait();
                            } catch (InterruptedException e) {
                                intr = true;
                            }
                            status = authentication.getStatus();
                        }
                        challenge = authentication.getSaslBytes();
                        authentication.setStatus(WAITING);
                        authentication.setSaslBytes(null);
                    }
                    if (status == CHALLENGE) {
                        try {
                            response = saslClient.evaluateChallenge(challenge);
                        } catch (SaslException e) {
                            log.tracef(e, "Mechanism failed (client): \"%s\"", saslClient.getMechanismName());
                            mechanisms.remove(saslClient.getMechanismName());
                            safeDispose(saslClient);
                            break;
                        }
                        try {
                            connectionHandler.sendAuthResponse(id, response);
                        } catch (IOException e) {
                            throw log.authenticationExceptionIo(e);
                        }
                    } else if (status == SUCCESS) {
                        if (challenge != null) {
                            try {
                                response = saslClient.evaluateChallenge(challenge);
                            } catch (SaslException e) {
                                log.tracef(e, "Mechanism failed (client, possibly failed to verify server): \"%s\"", saslClient.getMechanismName());
                                mechanisms.remove(saslClient.getMechanismName());
                                safeDispose(saslClient);
                                break;
                            }
                            if (response != null) {
                                try {
                                    connectionHandler.sendAuthDelete(id);
                                } catch (IOException ignored) {
                                    log.trace("Send failed", ignored);
                                }
                                safeDispose(saslClient);
                                throw log.authenticationExtraResponse();
                            }
                        }
                        safeDispose(saslClient);
                        // todo: we could use a phantom ref to clean up the ID, but the benefits are dubious
                        return constructIdentity(conf -> new ConnectionPeerIdentity(conf, principal, finalId, connection));
                    } else if (status == REJECT) {
                        // auth rejected (server)
                        try {
                            connectionHandler.sendAuthDelete(id);
                        } catch (IOException ignored) {
                            log.trace("Send failed", ignored);
                        }
                        safeDispose(saslClient);
                        throw log.serverRejectedAuthentication();
                    } else if (status == CLOSED) {
                        safeDispose(saslClient);
                        throw log.authenticationExceptionClosed();
                    } else if (status == DELETE) {
                        safeDispose(saslClient);
                        throw log.serverRejectedAuthentication();
                    } else {
                        throw Assert.unreachableCode();
                    }
                }
            }
            // calculate what mechanisms we've tried
            Set<String> triedMechs = new HashSet<>(offeredMechanisms);
            triedMechs.removeAll(mechanisms);
            // whatever is left is what we've tried
            Iterator<String> iterator = triedMechs.iterator();
            String triedStr;
            if (iterator.hasNext()) {
                StringBuilder b = new StringBuilder();
                b.append(iterator.next());
                while (iterator.hasNext()) {
                    b.append(',').append(iterator.next());
                }
                triedStr = b.toString();
            } else {
                triedStr = "(none)";
            }
            throw log.noAuthMechanismsLeft(triedStr);
        } finally {
            if (intr) Thread.currentThread().interrupt();
        }
    }

    private static void safeDispose(final SaslClient saslClient) {
        try {
            saslClient.dispose();
        } catch (SaslException ignored) {
        }
    }

    private static final int WAITING = 0;
    private static final int CHALLENGE = 1;
    private static final int SUCCESS = 2;
    private static final int REJECT = 3;
    private static final int DELETE = 4;
    private static final int CLOSED = 5;

    void receiveChallenge(final int id, final byte[] challenge) {
        final Authentication authentication = authMap.get(id);
        if (authentication != null) {
            synchronized (authentication) {
                authentication.setSaslBytes(challenge);
                authentication.setStatus(CHALLENGE);
                authentication.notify();
            }
        }
    }

    void receiveSuccess(final int id, final byte[] challenge) {
        final Authentication authentication = authMap.get(id);
        if (authentication != null) {
            synchronized (authentication) {
                authentication.setSaslBytes(challenge);
                authentication.setStatus(SUCCESS);
                authentication.notify();
            }
        }
    }

    void receiveReject(final int id) {
        final Authentication authentication = authMap.get(id);
        if (authentication != null) {
            synchronized (authentication) {
                authentication.setStatus(REJECT);
                authentication.notify();
            }
        }
    }

    void receiveDeleteAck(final int id) {
        final Authentication authentication = authMap.removeKey(id);
        if (authentication != null) {
            synchronized (authentication) {
                authentication.setStatus(DELETE);
                authentication.notify();
            }
        }
    }

    void connectionClosed() {
        Iterator<Authentication> iterator = authMap.iterator();
        while (iterator.hasNext()) {
            final Authentication authentication = iterator.next();
            iterator.remove();
            synchronized (authentication) {
                authentication.setStatus(CLOSED);
                authentication.notify();
            }
        }
    }

    /**
     * Get the anonymous identity for this context.
     *
     * @return the anonymous identity (not {@code null})
     */
    public ConnectionPeerIdentity getAnonymousIdentity() {
        return anonymousIdentity;
    }

    ConnectionPeerIdentity getConnectionIdentity() {
        return connectionIdentity;
    }

    /**
     * Get the current identity.
     *
     * @return the current identity (not {@code null})
     */
    public ConnectionPeerIdentity getCurrentIdentity() {
        return (ConnectionPeerIdentity) super.getCurrentIdentity();
    }

    static final class Authentication {

        private final int id;
        private byte[] saslBytes;
        private int status;

        Authentication(final int id) {
            this.id = id;
        }

        int getId() {
            return id;
        }

        byte[] getSaslBytes() {
            return saslBytes;
        }

        void setSaslBytes(final byte[] saslBytes) {
            this.saslBytes = saslBytes;
        }

        int getStatus() {
            return status;
        }

        void setStatus(final int status) {
            this.status = status;
        }
    }
}
