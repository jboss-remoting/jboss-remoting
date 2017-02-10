/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslClientFactory;

import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class FutureConnection {

    private final EndpointImpl endpoint;
    private final URI uri;
    private final String realHost;
    private final int realPort;
    private final AtomicReference<FutureResult<Connection>> futureConnectionRef = new AtomicReference<FutureResult<Connection>>();
    private final boolean immediate;
    private final OptionMap options;
    private final AuthenticationConfiguration configuration;
    private final UnaryOperator<SaslClientFactory> clientFactoryOperator;
    private final SSLContext sslContext;
    private final SocketAddress bindAddress;

    // TODO: make a PIContext for the connection that auto-re-auths to each new real connection

    FutureConnection(final EndpointImpl endpoint, final SocketAddress bindAddress, final URI uri, final String realHost, final int realPort, final boolean immediate, final OptionMap options, final AuthenticationConfiguration configuration, final UnaryOperator<SaslClientFactory> clientFactoryOperator, final SSLContext sslContext) {
        this.endpoint = endpoint;
        this.bindAddress = bindAddress;
        this.uri = uri;
        this.realHost = realHost;
        this.realPort = realPort;
        this.immediate = immediate;
        this.options = options;
        this.configuration = configuration;
        this.clientFactoryOperator = clientFactoryOperator;
        this.sslContext = sslContext;
    }

    void reconnectAfterDelay() {
        endpoint.getXnioWorker().getIoThread().executeAfter(() -> FutureConnection.this.init(true), 30L, TimeUnit.SECONDS);
    }

    IoFuture<Connection> init(boolean connect) {
        return getConnection(null, connect);
    }

    void splice(FutureResult<Connection> futureResult, IoFuture<Connection> realFuture) {
        // always add in this order
        futureResult.addCancelHandler(realFuture);
        realFuture.addNotifier(new IoFuture.HandlingNotifier<Connection, FutureResult<Connection>>() {
            public void handleCancelled(final FutureResult<Connection> attachment) {
                attachment.setCancelled();
            }

            public void handleFailed(final IOException exception, final FutureResult<Connection> attachment) {
                attachment.setException(exception);
            }

            public void handleDone(final Connection data, final FutureResult<Connection> attachment) {
                attachment.setResult(new ManagedConnection(data, FutureConnection.this, futureResult));
            }
        }, futureResult);
    }

    IoFuture<Connection> getConnection(FutureResult<Connection> orig, boolean connect) {
        AtomicReference<FutureResult<Connection>> futureConnectionRef = this.futureConnectionRef;
        FutureResult<Connection> oldVal;
        oldVal = futureConnectionRef.get();
        if (oldVal != orig) {
            return oldVal.getIoFuture();
        }
        if (! connect) {
            return null;
        }
        final FutureResult<Connection> futureResult = new FutureResult<>();
        while (! futureConnectionRef.compareAndSet(oldVal, futureResult)) {
            oldVal = futureConnectionRef.get();
            if (oldVal != orig) {
                // discard our new one
                return oldVal.getIoFuture();
            }
        }
        IoFuture<Connection> realFuture;
        realFuture = endpoint.connect(uri, bindAddress, options, configuration, clientFactoryOperator, sslContext);
        splice(futureResult, realFuture);
        final IoFuture<Connection> ioFuture = futureResult.getIoFuture();
        ioFuture.addNotifier(new IoFuture.HandlingNotifier<Connection, FutureConnection>() {
            public void handleCancelled(final FutureConnection attachment) {
                attachment.futureConnectionRef.set(null);
                if (attachment.immediate) {
                    attachment.reconnectAfterDelay();
                }
            }

            public void handleFailed(final IOException exception, final FutureConnection attachment) {
                attachment.futureConnectionRef.set(null);
                if (attachment.immediate) {
                    attachment.reconnectAfterDelay();
                }
            }

            public void handleDone(final Connection connection, final FutureConnection attachment) {
                connection.addCloseHandler((closed, exception) -> {
                    FutureConnection.this.clearRef(futureResult);
                    if (attachment.immediate) {
                        attachment.getConnection(attachment.futureConnectionRef.get(), true);
                    }
                });
            }
        }, this);
        return ioFuture;
    }

    void clearRef(FutureResult<Connection> futureResult) {
        futureConnectionRef.compareAndSet(futureResult, null);
    }

    public IoFuture<Connection> get(final boolean connect) {
        return init(connect);
    }

    boolean isConnected() {
        final FutureResult<Connection> futureResult = futureConnectionRef.get();
        return futureResult != null && futureResult.getIoFuture().getStatus() == IoFuture.Status.DONE;
    }
}
