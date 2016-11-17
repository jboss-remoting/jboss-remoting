/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.remoting3.remote;

import static org.jboss.remoting3._private.Messages.log;
import static org.xnio.IoUtils.safeClose;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.security.sasl.SaslClientFactory;

import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.wildfly.common.Assert;
import org.wildfly.security.SecurityFactory;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Result;
import org.xnio.StreamConnection;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.SslChannel;
import org.xnio.ssl.JsseSslConnection;
import org.xnio.ssl.SslConnection;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class RemoteConnectionProvider extends AbstractHandleableCloseable<ConnectionProvider> implements ConnectionProvider {

    static final boolean USE_POOLING;
    static final boolean LEAK_DEBUGGING;

    static {
        boolean usePooling = true;
        boolean leakDebugging = false;
        try {
            usePooling = Boolean.parseBoolean(System.getProperty("jboss.remoting.pooled-buffers", "true"));
            leakDebugging = Boolean.parseBoolean(System.getProperty("jboss.remoting.debug-buffer-leaks", "false"));
        } catch (Throwable ignored) {}
        USE_POOLING = usePooling;
        LEAK_DEBUGGING = leakDebugging;
    }

    private final ProviderInterface providerInterface = new ProviderInterface();
    private final XnioWorker xnioWorker;
    private final ConnectionProviderContext connectionProviderContext;
    private final boolean sslRequired;
    private final boolean sslEnabled;
    private final Collection<Cancellable> pendingInboundConnections = Collections.synchronizedSet(new HashSet<Cancellable>());
    private final Set<RemoteConnectionHandler> handlers = Collections.synchronizedSet(new HashSet<RemoteConnectionHandler>());
    private final MBeanServer server;
    private final ObjectName objectName;

    RemoteConnectionProvider(final OptionMap optionMap, final ConnectionProviderContext connectionProviderContext) throws IOException {
        super(connectionProviderContext.getExecutor());
        sslRequired = optionMap.get(Options.SECURE, false);
        sslEnabled = optionMap.get(Options.SSL_ENABLED, true);
        xnioWorker = connectionProviderContext.getXnioWorker();
        this.connectionProviderContext = connectionProviderContext;
        MBeanServer server = null;
        ObjectName objectName = null;
        try {
            server = ManagementFactory.getPlatformMBeanServer();
            objectName = new ObjectName("jboss.remoting.handler", "name", connectionProviderContext.getEndpoint().getName() + "-" + hashCode());
            server.registerMBean(new RemoteConnectionProviderMXBean() {
                public void dumpConnectionState() {
                    doDumpConnectionState();
                }

                public String dumpConnectionStateToString() {
                    return doGetConnectionState();
                }
            }, objectName);
        } catch (Exception e) {
            // ignore
        }
        this.server = server;
        this.objectName = objectName;
    }

    private void doDumpConnectionState() {
        final StringBuilder b = new StringBuilder();
        doGetConnectionState(b);
        log.info(b);
    }

    private void doGetConnectionState(final StringBuilder b) {
        b.append("Connection state for ").append(this).append(':').append('\n');
        synchronized (handlers) {
            for (RemoteConnectionHandler handler : handlers) {
                handler.dumpState(b);
            }
        }
    }

    private String doGetConnectionState() {
        final StringBuilder b = new StringBuilder();
        doGetConnectionState(b);
        return b.toString();
    }

    public Cancellable connect(final URI destination, final SocketAddress bindAddress, final OptionMap connectOptions, final Result<ConnectionHandlerFactory> result, final AuthenticationConfiguration authenticationConfiguration, final SecurityFactory<SSLContext> sslContextFactory, final UnaryOperator<SaslClientFactory> saslClientFactoryOperator, final Collection<String> serverMechs) {
        if (! isOpen()) {
            throw new IllegalStateException("Connection provider is closed");
        }
        Assert.checkNotNullParam("destination", destination);
        Assert.checkNotNullParam("connectOptions", connectOptions);
        Assert.checkNotNullParam("result", result);
        Assert.checkNotNullParam("authenticationConfiguration", authenticationConfiguration);
        Assert.checkNotNullParam("saslClientFactoryOperator", saslClientFactoryOperator);
        log.tracef("Attempting to connect to \"%s\" with options %s", destination, connectOptions);
        // cancellable that will be returned by this method
        final FutureResult<ConnectionHandlerFactory> cancellableResult = new FutureResult<ConnectionHandlerFactory>();
        cancellableResult.addCancelHandler(new Cancellable() {
            @Override
            public Cancellable cancel() {
                cancellableResult.setCancelled();
                return this;
            }
        });
        final IoFuture<ConnectionHandlerFactory> returnedFuture = cancellableResult.getIoFuture();
        returnedFuture.addNotifier(IoUtils.<ConnectionHandlerFactory>resultNotifier(), result);
        final boolean useSsl = sslRequired || sslEnabled && connectOptions.get(Options.SSL_ENABLED, true);
        final ChannelListener<StreamConnection> openListener = new ChannelListener<StreamConnection>() {
            public void handleEvent(final StreamConnection connection) {
                try {
                    connection.setOption(Options.TCP_NODELAY, Boolean.TRUE);
                } catch (IOException e) {
                    // ignore
                }
                final SslChannel sslChannel = connection instanceof SslChannel ? (SslChannel) connection : null;
                final RemoteConnection remoteConnection = new RemoteConnection(connection, sslChannel, connectOptions, RemoteConnectionProvider.this);
                cancellableResult.addCancelHandler(new Cancellable() {
                    @Override
                    public Cancellable cancel() {
                        RemoteConnectionHandler.sendCloseRequestBody(remoteConnection);
                        remoteConnection.handlePreAuthCloseRequest();
                        return this;
                    }
                });
                if (connection.isOpen()) {
                    remoteConnection.setResult(cancellableResult);
                    connection.getSinkChannel().setWriteListener(remoteConnection.getWriteListener());
                    final ClientConnectionOpenListener openListener = new ClientConnectionOpenListener(destination, remoteConnection, connectionProviderContext, authenticationConfiguration, saslClientFactoryOperator, serverMechs, connectOptions);
                    openListener.handleEvent(connection.getSourceChannel());
                }
            }
        };
        final AuthenticationContextConfigurationClient configurationClient = ClientConnectionOpenListener.AUTH_CONFIGURATION_CLIENT;
        final InetSocketAddress address = configurationClient.getDestinationInetSocketAddress(destination, authenticationConfiguration, 0);
        final IoFuture<? extends StreamConnection> future;
        if (useSsl) {
            future = createSslConnection(destination, (InetSocketAddress) bindAddress, address, connectOptions, authenticationConfiguration, sslContextFactory, openListener);
        } else {
            future = createConnection(destination, (InetSocketAddress) bindAddress, address, connectOptions, openListener);
        }
        pendingInboundConnections.add(returnedFuture);
        // if the connection fails, we need to propagate that
        future.addNotifier(new IoFuture.HandlingNotifier<StreamConnection, FutureResult<ConnectionHandlerFactory>>() {
            public void handleFailed(final IOException exception, final FutureResult<ConnectionHandlerFactory> attachment) {
                attachment.setException(exception);
            }

            public void handleCancelled(final FutureResult<ConnectionHandlerFactory> attachment) {
                attachment.setCancelled();
            }
        }, cancellableResult);
        returnedFuture.addNotifier(new IoFuture.HandlingNotifier<ConnectionHandlerFactory, IoFuture<ConnectionHandlerFactory>>() {
            public void handleCancelled(IoFuture<ConnectionHandlerFactory> attachment) {
                pendingInboundConnections.remove(attachment);
                future.cancel();
            }

            public void handleFailed(final IOException exception, IoFuture<ConnectionHandlerFactory> attachment) {
                pendingInboundConnections.remove(attachment);
            }

            public void handleDone(final ConnectionHandlerFactory data, IoFuture<ConnectionHandlerFactory> attachment) {
                pendingInboundConnections.remove(attachment);
            }
        }, returnedFuture);
        return returnedFuture;
    }

    protected IoFuture<StreamConnection> createConnection(final URI uri, final InetSocketAddress bindAddress, final InetSocketAddress destination, final OptionMap connectOptions, final ChannelListener<StreamConnection> openListener) {
        return bindAddress == null ?
               xnioWorker.openStreamConnection(destination, openListener, connectOptions) :
               xnioWorker.openStreamConnection(bindAddress, destination, openListener, null, connectOptions);
    }

    protected IoFuture<SslConnection> createSslConnection(final URI uri, final InetSocketAddress bindAddress, final InetSocketAddress destination, final OptionMap connectOptions, final AuthenticationConfiguration configuration, final SecurityFactory<SSLContext> sslContextFactory, final ChannelListener<StreamConnection> openListener) {
        final IoFuture<StreamConnection> futureConnection = bindAddress == null ?
                                                            xnioWorker.openStreamConnection(destination, null, connectOptions) :
                                                            xnioWorker.openStreamConnection(bindAddress, destination, null, null, connectOptions);
        final FutureResult<SslConnection> futureResult = new FutureResult<>(connectionProviderContext.getExecutor());
        futureResult.addCancelHandler(futureConnection);
        futureConnection.addNotifier(new IoFuture.HandlingNotifier<StreamConnection, FutureResult<SslConnection>>() {
            public void handleCancelled(final FutureResult<SslConnection> result) {
                result.setCancelled();
            }

            public void handleFailed(final IOException exception, final FutureResult<SslConnection> result) {
                result.setException(exception);
            }

            public void handleDone(final StreamConnection streamConnection, final FutureResult<SslConnection> result) {
                final AuthenticationContextConfigurationClient configurationClient = ClientConnectionOpenListener.AUTH_CONFIGURATION_CLIENT;
                final String realHost = configurationClient.getRealHost(uri, configuration);
                final int realPort = configurationClient.getRealPort(uri, configuration);
                final SSLEngine engine;
                try {
                    engine = sslContextFactory.create().createSSLEngine(realHost, realPort);
                    engine.setUseClientMode(true);
                } catch (GeneralSecurityException e) {
                    result.setException(new IOException(e));
                    safeClose(streamConnection);
                    return;
                }
                final JsseSslConnection sslConnection = new JsseSslConnection(streamConnection, engine);
                if (sslRequired || ! connectOptions.get(Options.SSL_STARTTLS, false)) try {
                    sslConnection.startHandshake();
                } catch (IOException e) {
                    result.setException(new IOException(e));
                    safeClose(streamConnection);
                    return;
                }
                result.setResult(sslConnection);
                openListener.handleEvent(sslConnection);
            }
        }, futureResult);
        return futureResult.getIoFuture();
    }

    public Object getProviderInterface() {
        return providerInterface;
    }

    protected void closeAction() {
        try {
            final Cancellable[] cancellables;
            synchronized (pendingInboundConnections) {
                cancellables = pendingInboundConnections.toArray(new Cancellable[pendingInboundConnections.size()]);
                pendingInboundConnections.clear();
            }
            for (Cancellable pendingConnection: cancellables) {
                pendingConnection.cancel();
            }
            closeComplete();
        } finally {
            if (server != null && objectName != null) {
                try {
                    server.unregisterMBean(objectName);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    void addConnectionHandler(final RemoteConnectionHandler connectionHandler) {
        handlers.add(connectionHandler);
    }

    void removeConnectionHandler(final RemoteConnectionHandler connectionHandler) {
        handlers.remove(connectionHandler);
    }

    final class ProviderInterface implements NetworkServerProvider {

        public AcceptingChannel<StreamConnection> createServer(final SocketAddress bindAddress, final OptionMap optionMap, final SaslAuthenticationFactory saslAuthenticationFactory, final SSLContext sslContext) throws IOException {
            Assert.checkNotNullParam("bindAddress", bindAddress);
            Assert.checkNotNullParam("optionMap", optionMap);
            Assert.checkNotNullParam("saslAuthenticationFactory", saslAuthenticationFactory);
            final AcceptingChannel<StreamConnection> result;
            // SSL_ENABLED only defaults to true when sslEnabled is set
            if (sslContext != null && (sslRequired || sslEnabled && optionMap.get(Options.SSL_ENABLED, true))) {
                result = xnioWorker.createStreamConnectionServer(bindAddress, channel -> {
                    final StreamConnection streamConnection = acceptAndConfigure(channel);
                    if (streamConnection == null) return;
                    final InetSocketAddress peerAddress = streamConnection.getPeerAddress(InetSocketAddress.class);
                    final String realHost;
                    final int realPort;
                    if (peerAddress != null) {
                        realHost = peerAddress.getHostString();
                        realPort = peerAddress.getPort();
                    } else {
                        realHost = null;
                        realPort = 0;
                    }
                    final SSLEngine engine;
                    engine = sslContext.createSSLEngine(realHost, realPort);
                    engine.setUseClientMode(false);
                    final JsseSslConnection sslConnection = new JsseSslConnection(streamConnection, engine);
                    if (sslRequired || ! optionMap.get(Options.SSL_STARTTLS, false)) try {
                        sslConnection.startHandshake();
                    } catch (IOException e) {
                        safeClose(sslConnection);
                        log.failedToAccept(e);
                    }
                    handleAccepted(sslConnection, sslConnection, optionMap, saslAuthenticationFactory);
                }, optionMap);
            } else {
                result = xnioWorker.createStreamConnectionServer(bindAddress, channel -> {
                    final StreamConnection streamConnection = acceptAndConfigure(channel);
                    if (streamConnection == null) return;
                    handleAccepted(streamConnection, null, optionMap, saslAuthenticationFactory);
                }, optionMap);
            }
            addCloseHandler((closed, exception) -> safeClose(result));
            result.resumeAccepts();
            return result;
        }

        private StreamConnection acceptAndConfigure(final AcceptingChannel<StreamConnection> channel) {
            final StreamConnection streamConnection;
            try {
                streamConnection = channel.accept();
            } catch (IOException e) {
                log.failedToAccept(e);
                return null;
            }
            if (streamConnection == null) {
                return null;
            }
            try {
                streamConnection.setOption(Options.TCP_NODELAY, Boolean.TRUE);
            } catch (IOException e) {
                // ignore
            }
            return streamConnection;
        }

        private void handleAccepted(final StreamConnection accepted, final SslChannel sslChannel, final OptionMap serverOptionMap, final SaslAuthenticationFactory saslAuthenticationFactory) {
            final RemoteConnection connection = new RemoteConnection(accepted, sslChannel, serverOptionMap, RemoteConnectionProvider.this);
            final ServerConnectionOpenListener openListener = new ServerConnectionOpenListener(connection, connectionProviderContext, saslAuthenticationFactory, serverOptionMap);
            accepted.getSinkChannel().setWriteListener(connection.getWriteListener());
            log.tracef("Accepted connection from %s to %s", connection.getPeerAddress(), connection.getLocalAddress());
            openListener.handleEvent(accepted.getSourceChannel());
        }
    }

    protected Executor getExecutor() {
        return super.getExecutor();
    }

    public String toString() {
        return String.format("Remoting remote connection provider %x for %s", Integer.valueOf(hashCode()), connectionProviderContext.getEndpoint());
    }

    protected XnioWorker getXnioWorker() {
        return xnioWorker;
    }

    public ConnectionProviderContext getConnectionProviderContext() {
        return connectionProviderContext;
    }
}
