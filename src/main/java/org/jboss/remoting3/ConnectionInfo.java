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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.wildfly.common.Assert;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.xnio.Cancellable;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ConnectionInfo {
    final OptionMap connectOptions;
    State state = new None();

    private static final IoFuture<Connection> RETRY = new EmptyIoFuture();

    ConnectionInfo(final OptionMap connectOptions) {
        this.connectOptions = connectOptions;
    }

    IoFuture<Connection> getConnection(final EndpointImpl endpoint, ConnectionKey key, AuthenticationConfiguration authenticationConfiguration, boolean doConnect) {
        IoFuture<Connection> result;
        State state;
        do {
            synchronized (this) {
                state = this.state;
            }
            result = state.getConnection(endpoint, key, authenticationConfiguration, doConnect);
        } while (result == RETRY);
        return result;
    }

    void connectionClosed(AuthenticationConfiguration authenticationConfiguration, final FutureResult<Connection> futureResult) {
        State state;
        do {
            synchronized (this) {
                state = this.state;
            }
        } while (! state.connectionClosed(authenticationConfiguration, futureResult));
    }

    abstract static class State {
        abstract IoFuture<Connection> getConnection(EndpointImpl endpoint, ConnectionKey key, AuthenticationConfiguration authenticationConfiguration, boolean doConnect);

        abstract boolean connectionClosed(AuthenticationConfiguration authenticationConfiguration, FutureResult<Connection> futureResult);
    }

    final class None extends State {
        IoFuture<Connection> getConnection(final EndpointImpl endpoint, final ConnectionKey key, final AuthenticationConfiguration authenticationConfiguration, boolean doConnect) {
            if (! doConnect) return null;
            State oldState;
            synchronized (ConnectionInfo.this) {
                oldState = state;
                if (oldState == this) {
                    final IoFuture<Connection> attempt = endpoint.connect(key.getRealUri(), null, connectOptions, key.getSslContext(), authenticationConfiguration);
                    final MaybeShared maybeShared = new MaybeShared(authenticationConfiguration, attempt);
                    final FutureResult<Connection> futureResult = new FutureResult<>();
                    splice(futureResult, attempt, authenticationConfiguration);
                    attempt.addNotifier(new IoFuture.HandlingNotifier<Connection, Void>() {
                        public void handleCancelled(final Void attachment) {
                            clear();
                        }

                        public void handleFailed(final IOException exception, final Void attachment) {
                            clear();
                        }

                        public void handleDone(final Connection connection, final Void attachment) {
                            final ConnectionInfo outer = ConnectionInfo.this;
                            synchronized (outer) {
                                assert state == maybeShared;
                                // transition to the next state and resolve all pending attempts
                                if (connection.supportsRemoteAuth()) {
                                    // shared!
                                    state = new Shared(futureResult, Collections.emptyMap());
                                } else {
                                    // unsharable :(
                                    state = new NotShared(Collections.singletonMap(authenticationConfiguration, futureResult));
                                }
                                synchronized (maybeShared.pendingAttempts) {
                                    for (Map.Entry<AuthenticationConfiguration, FutureResult<Connection>> pendingAttempt : maybeShared.pendingAttempts.entrySet()) {
                                        final AuthenticationConfiguration pendingAuthenticationConfiguration = pendingAttempt.getKey();
                                        final FutureResult<Connection> pendingFutureResult = pendingAttempt.getValue();
                                        final IoFuture<Connection> realAttempt = outer.getConnection(endpoint, key, pendingAuthenticationConfiguration, true);
                                        splice(pendingFutureResult, realAttempt, pendingAuthenticationConfiguration);
                                    }
                                }
                            }
                        }

                        private void clear() {
                            final ConnectionInfo outer = ConnectionInfo.this;
                            synchronized (outer) {
                                assert state == maybeShared;
                                state = None.this;
                            }
                        }
                    }, null);
                    state = maybeShared;
                    return futureResult.getIoFuture();
                }
            }
            // try again :(
            return RETRY;
        }

        boolean connectionClosed(final AuthenticationConfiguration authenticationConfiguration, final FutureResult<Connection> futureResult) {
            // we can't possibly care; this is probably a bug, even
            return true;
        }
    }

    final class MaybeShared extends State {
        private final AuthenticationConfiguration authenticationConfiguration;
        private final IoFuture<Connection> attempt;
        private final Map<AuthenticationConfiguration, FutureResult<Connection>> pendingAttempts = new HashMap<>();

        MaybeShared(final AuthenticationConfiguration authenticationConfiguration, final IoFuture<Connection> attempt) {
            this.authenticationConfiguration = authenticationConfiguration;
            this.attempt = attempt;
        }

        IoFuture<Connection> getConnection(final EndpointImpl endpoint, final ConnectionKey key, final AuthenticationConfiguration authenticationConfiguration, boolean doConnect) {
            synchronized (pendingAttempts) {
                if (authenticationConfiguration.equals(this.authenticationConfiguration)) {
                    return attempt;
                } else {
                    FutureResult<Connection> futureResult = pendingAttempts.get(authenticationConfiguration);
                    if (futureResult != null) {
                        return futureResult.getIoFuture();
                    } else {
                        if (! doConnect) {
                            return null;
                        }
                        futureResult = new FutureResult<>(endpoint.getExecutor());
                        pendingAttempts.put(authenticationConfiguration, futureResult);
                    }
                    assert doConnect;
                    final IoFuture<Connection> ioFuture = futureResult.getIoFuture();
                    final AtomicBoolean cancelFlag = new AtomicBoolean();
                    final FutureResult<Connection> finalFutureResult = futureResult;
                    futureResult.addCancelHandler(new Cancellable() {
                        public Cancellable cancel() {
                            cancelFlag.set(true);
                            finalFutureResult.setCancelled();
                            return this;
                        }
                    });
                    return ioFuture;
                }
            }
        }

        boolean connectionClosed(final AuthenticationConfiguration authenticationConfiguration, final FutureResult<Connection> futureResult) {
            // an early notification... we should be done though, so synchronize and retry
            attempt.await();
            // try again
            return false;
        }
    }

    final class Shared extends State {
        private final FutureResult<Connection> sharedConnection;
        private final Map<AuthenticationConfiguration, FutureResult<Connection>> leftovers;

        Shared(final FutureResult<Connection> sharedConnection, final Map<AuthenticationConfiguration, FutureResult<Connection>> leftovers) {
            this.sharedConnection = sharedConnection;
            this.leftovers = leftovers;
        }

        IoFuture<Connection> getConnection(final EndpointImpl endpoint, final ConnectionKey key, final AuthenticationConfiguration authenticationConfiguration, boolean doConnect) {
            return leftovers.getOrDefault(authenticationConfiguration, sharedConnection).getIoFuture();
        }

        boolean connectionClosed(final AuthenticationConfiguration authenticationConfiguration, final FutureResult<Connection> futureResult) {
            final State newState;
            if (futureResult == sharedConnection) {
                // shared connection closed :-(
                if (leftovers.isEmpty()) {
                    newState = new None();
                } else {
                    // not ideal, but also extremely unlikely
                    newState = new NotShared(leftovers);
                }
            } else {
                final FutureResult<Connection> mapVal = leftovers.get(authenticationConfiguration);
                if (! futureResult.equals(mapVal)) {
                    // nothing to do
                    return true;
                }
                // swap map, maybe
                Map<AuthenticationConfiguration, FutureResult<Connection>> newMap;
                if (leftovers.size() == 1) {
                    newMap = Collections.emptyMap();
                } else {
                    newMap = new HashMap<>(leftovers);
                    newMap.remove(authenticationConfiguration);
                }
                newState = new Shared(sharedConnection, newMap);
            }
            synchronized (ConnectionInfo.this) {
                if (state == this) {
                    state = newState;
                    return true;
                }
            }
            // try again :(
            return false;
        }
    }

    final class NotShared extends State {
        private final Map<AuthenticationConfiguration, FutureResult<Connection>> connections;

        NotShared(final Map<AuthenticationConfiguration, FutureResult<Connection>> connections) {
            this.connections = connections;
        }

        IoFuture<Connection> getConnection(final EndpointImpl endpoint, final ConnectionKey key, final AuthenticationConfiguration authenticationConfiguration, boolean doConnect) {
            final FutureResult<Connection> future = connections.get(authenticationConfiguration);
            if (future != null) {
                return future.getIoFuture();
            }
            if (! doConnect) {
                return null;
            }
            // add a new unshared connection
            State oldState;
            synchronized (ConnectionInfo.this) {
                oldState = ConnectionInfo.this.state;
                if (oldState == this) {
                    final IoFuture<Connection> attempt = endpoint.connect(key.getRealUri(), null, connectOptions, key.getSslContext(), authenticationConfiguration);
                    Map<AuthenticationConfiguration, FutureResult<Connection>> newConnections = new HashMap<>(connections);
                    final FutureResult<Connection> futureResult = new FutureResult<>();
                    splice(futureResult, attempt, authenticationConfiguration);
                    newConnections.put(authenticationConfiguration, futureResult);
                    return attempt;
                }
            }
            // try again :(
            return RETRY;
        }

        boolean connectionClosed(final AuthenticationConfiguration authenticationConfiguration, final FutureResult<Connection> futureResult) {
            final FutureResult<Connection> mapVal = connections.get(authenticationConfiguration);
            if (! futureResult.equals(mapVal)) {
                // nothing to do
                return true;
            }
            // swap map, maybe
            final State newState;
            if (connections.size() == 1) {
                newState = new None();
            } else {
                Map<AuthenticationConfiguration, FutureResult<Connection>> newMap = new HashMap<>(connections);
                newMap.remove(authenticationConfiguration);
                newState = new NotShared(newMap);
            }
            synchronized (ConnectionInfo.this) {
                if (state == this) {
                    state = newState;
                    return true;
                }
            }
            // try again :(
            return false;
        }
    }

    void splice(FutureResult<Connection> futureResult, IoFuture<Connection> realFuture, final AuthenticationConfiguration authConfig) {
        // always add in this order
        futureResult.addCancelHandler(realFuture);
        realFuture.addNotifier(new IoFuture.HandlingNotifier<Connection, FutureResult<Connection>>() {
            public void handleCancelled(final FutureResult<Connection> futureResult1) {
                futureResult1.setCancelled();
            }

            public void handleFailed(final IOException exception, final FutureResult<Connection> futureResult1) {
                futureResult1.setException(exception);
            }

            public void handleDone(final Connection connection, final FutureResult<Connection> futureResult1) {
                futureResult1.setResult(new ManagedConnection(connection, ConnectionInfo.this, authConfig, futureResult1));
            }
        }, futureResult);
    }

    static class EmptyIoFuture implements IoFuture<Connection> {
        public IoFuture<Connection> cancel() {
            throw Assert.unsupported();
        }

        public Status getStatus() {
            throw Assert.unsupported();
        }

        public Status await() {
            throw Assert.unsupported();
        }

        public Status await(final long time, final TimeUnit timeUnit) {
            throw Assert.unsupported();
        }

        public Status awaitInterruptibly() throws InterruptedException {
            throw Assert.unsupported();
        }

        public Status awaitInterruptibly(final long time, final TimeUnit timeUnit) throws InterruptedException {
            throw Assert.unsupported();
        }

        public Connection get() throws IOException, CancellationException {
            throw Assert.unsupported();
        }

        public Connection getInterruptibly() throws IOException, InterruptedException, CancellationException {
            throw Assert.unsupported();
        }

        public IOException getException() throws IllegalStateException {
            throw Assert.unsupported();
        }

        public <A> IoFuture<Connection> addNotifier(final Notifier<? super Connection, A> notifier, final A attachment) {
            throw Assert.unsupported();
        }
    }
}
