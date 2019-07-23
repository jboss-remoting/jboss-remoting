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

import static java.security.AccessController.doPrivileged;
import static org.jboss.remoting3._private.Messages.log;

import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import javax.net.ssl.SSLSession;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslException;

import org.jboss.remoting3._private.IntIndexHashMap;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.AuthenticationException;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.client.PeerIdentityContext;
import org.wildfly.security.auth.principal.AnonymousPrincipal;
import org.wildfly.security.sasl.WildFlySasl;
import org.wildfly.security.sasl.util.ProtocolSaslClientFactory;
import org.wildfly.security.sasl.util.ServerNameSaslClientFactory;
import org.xnio.Cancellable;
import org.xnio.FinishedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;

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
    private final FinishedIoFuture<ConnectionPeerIdentity> connectionIdentityFuture;
    private final FinishedIoFuture<ConnectionPeerIdentity> anonymousIdentityFuture;
    private final IntIndexHashMap<Authentication> authMap = new IntIndexHashMap<Authentication>(Authentication::getId);
    private final ConcurrentHashMap<AuthenticationConfiguration, IoFuture<ConnectionPeerIdentity>> futureAuths = new ConcurrentHashMap<>();
    private final UnaryOperator<SaslClientFactory> factoryOperator;

    private static final AuthenticationContextConfigurationClient CLIENT = doPrivileged((PrivilegedAction<AuthenticationContextConfigurationClient>) AuthenticationContextConfigurationClient::new);

    ConnectionPeerIdentityContext(final ConnectionImpl connection, final Collection<String> offeredMechanisms, final String peerSaslServer, final String saslProtocol) {
        this.connection = connection;
        this.offeredMechanisms = offeredMechanisms == null ? Collections.emptySet() : offeredMechanisms;
        connectionIdentity = constructIdentity(conf -> new ConnectionPeerIdentity(conf, connection.getPrincipal(), 0, connection));
        connectionIdentityFuture = new FinishedIoFuture<>(connectionIdentity);
        anonymousIdentity = constructIdentity(conf -> new ConnectionPeerIdentity(conf, AnonymousPrincipal.getInstance(), 1, connection));
        anonymousIdentityFuture = new FinishedIoFuture<>(anonymousIdentity);
        this.factoryOperator = d -> new ServerNameSaslClientFactory(new ProtocolSaslClientFactory(d, saslProtocol), peerSaslServer);
    }

    private static final Object PENDING = new Object();
    private static final Object CANCELLED = new Object();

    public IoFuture<ConnectionPeerIdentity> authenticateAsync(final AuthenticationConfiguration configuration) {
        Assert.checkNotNullParam("configuration", configuration);
        if (configuration.equals(connection.getAuthenticationConfiguration())) {
            return connectionIdentityFuture;
        } else if (CLIENT.getAuthorizationPrincipal(configuration) instanceof AnonymousPrincipal) {
            return anonymousIdentityFuture;
        }
        IoFuture<ConnectionPeerIdentity> ioFuture = futureAuths.get(configuration);
        if (ioFuture != null) {
            return ioFuture;
        }
        final FutureResult<ConnectionPeerIdentity> futureResult = new FutureResult<>(connection.getEndpoint().getExecutor());
        ioFuture = futureAuths.putIfAbsent(configuration, futureResult.getIoFuture());
        if (ioFuture != null) {
            return ioFuture;
        }
        final AtomicReference<Object> statRef = new AtomicReference<>(PENDING);
        connection.getEndpoint().getExecutor().execute(() -> {
            Object oldVal;
            do {
                oldVal = statRef.get();
                if (oldVal == CANCELLED) {
                    return;
                }
            } while (! statRef.compareAndSet(PENDING, Thread.currentThread()));
            try {
                futureResult.setResult(authenticate(configuration));
            } catch (AuthenticationException e) {
                futureResult.setException(e);
            }
            statRef.set(null);
        });
        futureResult.addCancelHandler(new Cancellable() {
            public Cancellable cancel() {
                Object oldVal;
                do {
                    oldVal = statRef.get();
                    if (oldVal == CANCELLED) {
                        return this;
                    }
                    if (oldVal instanceof Thread) {
                        ((Thread) oldVal).interrupt();
                        return this;
                    }
                } while (! statRef.compareAndSet(PENDING, CANCELLED));
                return this;
            }
        });
        return futureResult.getIoFuture();
    }

    public ConnectionPeerIdentity getExistingIdentity(final AuthenticationConfiguration configuration) throws AuthenticationException {
        if (configuration.equals(connection.getAuthenticationConfiguration())) {
            return connectionIdentity;
        } else if (CLIENT.getAuthorizationPrincipal(configuration) instanceof AnonymousPrincipal) {
            return anonymousIdentity;
        }
        return null;
    }

    /**
     * Perform an authentication.
     *
     * @param configuration the authentication configuration to use (must not be {@code null})
     * @return the peer identity (not {@code null})
     * @throws AuthenticationException if the authentication attempt failed
     */
    public ConnectionPeerIdentity authenticate(final AuthenticationConfiguration configuration) throws AuthenticationException {
        if (configuration.equals(connection.getAuthenticationConfiguration())) {
            return connectionIdentity;
        } else if (CLIENT.getAuthorizationPrincipal(configuration) instanceof AnonymousPrincipal) {
            return anonymousIdentity;
        }
        IoFuture<ConnectionPeerIdentity> ioFuture = futureAuths.get(configuration);
        if (ioFuture == null) {
            FutureResult<ConnectionPeerIdentity> futureResult = new FutureResult<>(connection.getEndpoint().getExecutor());
            final IoFuture<ConnectionPeerIdentity> appearing = futureAuths.putIfAbsent(configuration, futureResult.getIoFuture());
            if (appearing != null) {
                ioFuture = appearing;
            } else {
                AtomicReference<Thread> threadRef = new AtomicReference<>(Thread.currentThread());
                futureResult.addCancelHandler(new Cancellable() {
                    public Cancellable cancel() {
                        final Thread thread = threadRef.get();
                        if (thread != null) {
                            thread.interrupt();
                        }
                        return this;
                    }
                });
                try {
                    doAuthenticate(configuration, futureResult);
                } finally {
                    threadRef.set(null);
                }
                ioFuture = futureResult.getIoFuture();
            }
        }
        try {
            return ioFuture.get();
        } catch (AuthenticationException e) {
            throw e;
        } catch (IOException e) {
            throw new AuthenticationException(e);
        }
    }

    void doAuthenticate(final AuthenticationConfiguration configuration, FutureResult<ConnectionPeerIdentity> futureResult) {
        Assert.checkNotNullParam("configuration", configuration);
        final ConnectionImpl connection = this.connection;
        assert ! configuration.equals(connection.getAuthenticationConfiguration());
        if (! connection.supportsRemoteAuth()) {
            futureResult.setException(log.authenticationNotSupported());
            futureAuths.remove(configuration, futureResult.getIoFuture());
            return;
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
            futureResult.setException(log.authenticationInterrupted());
            futureAuths.remove(configuration, futureResult.getIoFuture());
            return;
        }
        try {
            final Principal principal = client.getPrincipal(configuration);
            final ConnectionHandler connectionHandler = connection.getConnectionHandler();
            // try each mech in turn, unless the peer explicitly rejects
            Set<String> mechanisms = new LinkedHashSet<>(offeredMechanisms);
            final LinkedHashMap<String, Throwable> triedMechs = new LinkedHashMap<>();
            while (! mechanisms.isEmpty()) {
                final SSLSession sslSession = connectionHandler.getSslSession();
                UnaryOperator<SaslClientFactory> factoryOperator = this.factoryOperator;
                try {
                    saslClient = client.createSaslClient(connection.getPeerURI(), configuration, mechanisms, factoryOperator, sslSession);
                } catch (SaslException e) {
                    futureResult.setException(log.authenticationNoSaslClient(e));
                    futureAuths.remove(configuration, futureResult.getIoFuture());
                    return;
                }
                if (saslClient == null) {
                    // break out to "no mechs left" error
                    break;
                }
                byte[] response;
                try {
                    if (saslClient.hasInitialResponse()) {
                        try {
                            response = saslClient.evaluateChallenge(NO_BYTES);
                        } catch (SaslException e) {
                            log.tracef(e, "Mechanism failed (client): \"%s\"", saslClient.getMechanismName());
                            mechanisms.remove(saslClient.getMechanismName());
                            triedMechs.put(saslClient.getMechanismName(), log.authenticationExceptionIo(e));
                            safeDispose(saslClient);
                            continue;
                        }
                    } else {
                        response = null;
                    }
                    connectionHandler.sendAuthRequest(id, saslClient.getMechanismName(), response);
                    if (! connectionHandler.isOpen()) {
                        safeDispose(saslClient);
                        futureResult.setException(log.authenticationExceptionClosed());
                        futureAuths.remove(configuration, futureResult.getIoFuture());
                        return;
                    }
                } catch (IOException e) {
                    authMap.remove(authentication);
                    safeDispose(saslClient);
                    futureResult.setException(log.authenticationExceptionIo(e));
                    futureAuths.remove(configuration, futureResult.getIoFuture());
                    return;
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
                            triedMechs.put(saslClient.getMechanismName(), log.authenticationExceptionIo(e));
                            safeDispose(saslClient);
                            break;
                        }
                        try {
                            connectionHandler.sendAuthResponse(id, response);
                            if (! connectionHandler.isOpen()) {
                                safeDispose(saslClient);
                                futureResult.setException(log.authenticationExceptionClosed());
                                futureAuths.remove(configuration, futureResult.getIoFuture());
                                return;
                            }
                        } catch (IOException e) {
                            safeDispose(saslClient);
                            futureResult.setException(log.authenticationExceptionIo(e));
                            futureAuths.remove(configuration, futureResult.getIoFuture());
                            return;
                        }
                        // retry loop
                    } else if (status == SUCCESS) {
                        if (! saslClient.isComplete()) {
                            try {
                                response = saslClient.evaluateChallenge(challenge);
                            } catch (SaslException e) {
                                log.tracef(e, "Mechanism failed (client, possibly failed to verify server): \"%s\"", saslClient.getMechanismName());
                                mechanisms.remove(saslClient.getMechanismName());
                                triedMechs.put(saslClient.getMechanismName(), log.authenticationExceptionIo(e));
                                safeDispose(saslClient);
                                break;
                            }
                            if (response != null && response.length > 0) {
                                try {
                                    connectionHandler.sendAuthDelete(id);
                                } catch (IOException ignored) {
                                    log.trace("Send failed", ignored);
                                }
                                safeDispose(saslClient);
                                futureResult.setException(log.authenticationExtraResponse());
                                futureAuths.remove(configuration, futureResult.getIoFuture());
                                return;
                            }
                        }
                        final Object principalObj = saslClient.getNegotiatedProperty(WildFlySasl.PRINCIPAL);
                        safeDispose(saslClient);
                        // todo: we could use a phantom ref to clean up the ID, but the benefits are dubious
                        futureResult.setResult(constructIdentity(conf -> {
                            return new ConnectionPeerIdentity(conf, principalObj instanceof Principal ? (Principal) principalObj : principal, finalId, connection);
                        }));
                        return;
                    } else if (status == REJECT) {
                        // auth rejected (server)
                        log.tracef("Mechanism failed (client received authentication rejected): \"%s\"", saslClient.getMechanismName());
                        mechanisms.remove(saslClient.getMechanismName());
                        triedMechs.put(saslClient.getMechanismName(), log.serverRejectedAuthentication());
                        safeDispose(saslClient);
                        break;
                    } else if (status == CLOSED) {
                        safeDispose(saslClient);
                        futureResult.setException(log.authenticationExceptionClosed());
                        futureAuths.remove(configuration, futureResult.getIoFuture());
                        return;
                    } else if (status == DELETE) {
                        safeDispose(saslClient);
                        futureResult.setException(log.serverRejectedAuthentication());
                        futureAuths.remove(configuration, futureResult.getIoFuture());
                        return;
                    } else {
                        throw Assert.unreachableCode();
                    }
                }
            }
            // no mechs left to try
            String triedStr;
            if (! triedMechs.isEmpty()) {
                final StringBuilder b = new StringBuilder();
                triedMechs.forEach((mechanismName, throwable) -> b.append("\n   ").append(mechanismName).append(": ").append(throwable.toString()));
                triedStr = b.toString();
            } else {
                triedStr = "(none)";
            }
            futureResult.setException(log.noAuthMechanismsLeft(triedStr));
            futureAuths.remove(configuration, futureResult.getIoFuture());
            return;
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
                authentication.notifyAll();
            }
        }
    }

    void receiveSuccess(final int id, final byte[] challenge) {
        final Authentication authentication = authMap.get(id);
        if (authentication != null) {
            synchronized (authentication) {
                authentication.setSaslBytes(challenge);
                authentication.setStatus(SUCCESS);
                authentication.notifyAll();
            }
        }
    }

    void receiveReject(final int id) {
        final Authentication authentication = authMap.get(id);
        if (authentication != null) {
            synchronized (authentication) {
                authentication.setStatus(REJECT);
                authentication.notifyAll();
            }
        }
    }

    void receiveDeleteAck(final int id) {
        final Authentication authentication = authMap.removeKey(id);
        if (authentication != null) {
            synchronized (authentication) {
                authentication.setStatus(DELETE);
                authentication.notifyAll();
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
                authentication.notifyAll();
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
        final ConnectionPeerIdentity currentIdentity = (ConnectionPeerIdentity) super.getCurrentIdentity();
        return currentIdentity == null ? anonymousIdentity : currentIdentity;
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
