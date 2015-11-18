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

import static org.jboss.remoting3.remote.RemoteLogger.log;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.net.ssl.SSLEngine;
import javax.security.sasl.SaslClientFactory;

import java.util.Set;
import java.util.concurrent.Executor;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.xnio.AbstractConvertingIoFuture;
import org.xnio.BufferAllocator;
import org.xnio.Buffers;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.Result;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.AssembledConnectedSslStreamChannel;
import org.xnio.channels.AssembledConnectedStreamChannel;
import org.xnio.channels.ConnectedSslStreamChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.FramedMessageChannel;
import org.xnio.ssl.JsseSslConnection;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("deprecation")
class RemoteConnectionProvider extends AbstractHandleableCloseable<ConnectionProvider> implements ConnectionProvider {

    static final boolean USE_POOLING;
    static final boolean LEAK_DEBUGGING;

    static {
        boolean usePooling = true;
        boolean leakDebugging = false;
        try {
            usePooling = Boolean.parseBoolean(System.getProperty("jboss.remoting.pooled-buffers", "false"));
            leakDebugging = Boolean.parseBoolean(System.getProperty("jboss.remoting.debug-buffer-leaks", "false"));
        } catch (Throwable ignored) {}
        USE_POOLING = usePooling;
        LEAK_DEBUGGING = leakDebugging;
    }

    private final ProviderInterface providerInterface = new ProviderInterface();
    private final Xnio xnio;
    private final XnioWorker xnioWorker;
    private final ConnectionProviderContext connectionProviderContext;
    private final boolean sslRequired;
    private final boolean sslEnabled;
    private final Collection<Cancellable> pendingInboundConnections = Collections.synchronizedSet(new HashSet<Cancellable>());
    private final Set<RemoteConnectionHandler> handlers = Collections.synchronizedSet(new HashSet<RemoteConnectionHandler>());
    private final MBeanServer server;
    private final ObjectName objectName;
    private final int defaultBufferSize;

    RemoteConnectionProvider(final OptionMap optionMap, final ConnectionProviderContext connectionProviderContext) throws IOException {
        super(connectionProviderContext.getExecutor());
        xnio = connectionProviderContext.getXnio();
        sslRequired = optionMap.get(Options.SECURE, false);
        sslEnabled = optionMap.get(Options.SSL_ENABLED, true);
        xnioWorker = connectionProviderContext.getXnioWorker();
        this.connectionProviderContext = connectionProviderContext;
        defaultBufferSize = optionMap.get(RemotingOptions.RECEIVE_BUFFER_SIZE, RemotingOptions.DEFAULT_RECEIVE_BUFFER_SIZE);
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
        RemoteLogger.log.info(b);
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

    public Cancellable connect(final URI destination, final OptionMap connectOptions, final Result<ConnectionHandlerFactory> result, final AuthenticationContext authenticationContext, final SaslClientFactory saslClientFactory) {
        if (! isOpen()) {
            throw new IllegalStateException("Connection provider is closed");
        }
        Assert.checkNotNullParam("destination", destination);
        Assert.checkNotNullParam("connectOptions", connectOptions);
        Assert.checkNotNullParam("result", result);
        Assert.checkNotNullParam("authenticationContext", authenticationContext);
        Assert.checkNotNullParam("saslClientFactory", saslClientFactory);
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
        final boolean sslCapable = sslEnabled;
        final boolean useSsl = sslRequired || sslCapable && connectOptions.get(Options.SSL_ENABLED, true) && !connectOptions.get(Options.SECURE, false);
        final ChannelListener<ConnectedStreamChannel> openListener = new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                try {
                    channel.setOption(Options.TCP_NODELAY, Boolean.TRUE);
                } catch (IOException e) {
                    // ignore
                }
                final int messageBufferSize = defaultBufferSize;
                Pool<ByteBuffer> messageBufferPool = USE_POOLING ? new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, messageBufferSize, messageBufferSize * 2) : Buffers.allocatedBufferPool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, messageBufferSize);
                if (LEAK_DEBUGGING) messageBufferPool = new DebuggingBufferPool(messageBufferPool);
                final int framingBufferSize = messageBufferSize + 4;
                final FramedMessageChannel messageChannel = new FramedMessageChannel(channel, ByteBuffer.allocate(framingBufferSize), ByteBuffer.allocate(framingBufferSize));
                final RemoteConnection remoteConnection = new RemoteConnection(messageBufferPool, channel, messageChannel, connectOptions, RemoteConnectionProvider.this);
                cancellableResult.addCancelHandler(new Cancellable() {
                    @Override
                    public Cancellable cancel() {
                        RemoteConnectionHandler.sendCloseRequestBody(remoteConnection);
                        remoteConnection.handlePreAuthCloseRequest();
                        return this;
                    }
                });
                if (messageChannel.isOpen()) {
                    remoteConnection.setResult(cancellableResult);
                    messageChannel.getWriteSetter().set(remoteConnection.getWriteListener());
                    final ClientConnectionOpenListener openListener = new ClientConnectionOpenListener(destination, remoteConnection, connectionProviderContext, authenticationContext, saslClientFactory, connectOptions);
                    openListener.handleEvent(messageChannel);
                }
            }
        };
        final AuthenticationContextConfigurationClient configurationClient = ClientConnectionOpenListener.AUTH_CONFIGURATION_CLIENT;
        final AuthenticationConfiguration authenticationConfiguration = configurationClient.getAuthenticationConfiguration(destination, authenticationContext);
        final InetSocketAddress address = configurationClient.getDestinationInetSocketAddress(destination, authenticationConfiguration, 0);
        final IoFuture<? extends ConnectedStreamChannel> future;
        if (useSsl) {
            future = createSslConnection(destination, address, connectOptions, authenticationContext, openListener);
        } else {
            future = createConnection(destination, address, connectOptions, openListener);
        }
        pendingInboundConnections.add(returnedFuture);
        // if the connection fails, we need to propagate that
        future.addNotifier(new IoFuture.HandlingNotifier<ConnectedStreamChannel, FutureResult<ConnectionHandlerFactory>>() {
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

    protected IoFuture<ConnectedStreamChannel> createConnection(final URI uri, final InetSocketAddress destination, final OptionMap connectOptions, final ChannelListener<ConnectedStreamChannel> openListener) {
        final AbstractConvertingIoFuture<ConnectedStreamChannel, StreamConnection> future = new AbstractConvertingIoFuture<ConnectedStreamChannel, StreamConnection>(xnioWorker.openStreamConnection(destination, null, connectOptions)) {
            protected ConnectedStreamChannel convert(final StreamConnection streamConnection) throws IOException {
                return new AssembledConnectedStreamChannel(streamConnection, streamConnection.getSourceChannel(), streamConnection.getSinkChannel());
            }
        };
        future.addNotifier(new IoFuture.HandlingNotifier<ConnectedStreamChannel, Void>() {
            public void handleDone(final ConnectedStreamChannel data, final Void attachment) {
                openListener.handleEvent(data);
            }
        }, null);
        return future;
    }

    protected IoFuture<ConnectedSslStreamChannel> createSslConnection(final URI uri, final InetSocketAddress destination, final OptionMap connectOptions, final AuthenticationContext authenticationContext, final ChannelListener<ConnectedStreamChannel> openListener) {
        final AbstractConvertingIoFuture<ConnectedSslStreamChannel, StreamConnection> future = new AbstractConvertingIoFuture<ConnectedSslStreamChannel, StreamConnection>(xnioWorker.openStreamConnection(destination, null, connectOptions)) {
            protected ConnectedSslStreamChannel convert(final StreamConnection streamConnection) throws IOException {
                final AuthenticationContextConfigurationClient configurationClient = ClientConnectionOpenListener.AUTH_CONFIGURATION_CLIENT;
                final AuthenticationConfiguration configuration = configurationClient.getAuthenticationConfiguration(uri, authenticationContext);
                final String realHost = configurationClient.getRealHost(uri, configuration);
                final int realPort = configurationClient.getRealPort(uri, configuration);
                final SSLEngine engine;
                try {
                    engine = configurationClient.getSslContext(configuration).createSSLEngine(realHost, realPort);
                } catch (GeneralSecurityException e) {
                    throw new IOException(e);
                }
                final JsseSslConnection sslConnection = new JsseSslConnection(streamConnection, engine);
                return new AssembledConnectedSslStreamChannel(sslConnection, sslConnection.getSourceChannel(), sslConnection.getSinkChannel());
            }
        };
        future.addNotifier(new IoFuture.HandlingNotifier<ConnectedSslStreamChannel, Void>() {
            public void handleDone(final ConnectedSslStreamChannel data, final Void attachment) {
                openListener.handleEvent(data);
            }
        }, null);
        return future;
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

        public AcceptingChannel<? extends ConnectedStreamChannel> createServer(final SocketAddress bindAddress, final OptionMap optionMap, final SaslAuthenticationFactory saslAuthenticationFactory) throws IOException {
            final boolean sslCapable = sslEnabled;
            final AcceptListener acceptListener = new AcceptListener(optionMap, saslAuthenticationFactory);
            final AcceptingChannel<? extends ConnectedStreamChannel> result;
            if (sslCapable && optionMap.get(Options.SSL_ENABLED, true)) {
                // todo
                result = xnioWorker.createStreamServer(bindAddress, acceptListener, optionMap);
//                result = xnioSsl.createSslTcpServer(xnioWorker, (InetSocketAddress) bindAddress, acceptListener, optionMap);
            } else {
                result = xnioWorker.createStreamServer(bindAddress, acceptListener, optionMap);
            }
            addCloseHandler((closed, exception) -> IoUtils.safeClose(result));
            result.resumeAccepts();
            return result;
        }
    }

    protected Executor getExecutor() {
        return super.getExecutor();
    }

    private final class AcceptListener implements ChannelListener<AcceptingChannel<? extends ConnectedStreamChannel>> {

        private final OptionMap serverOptionMap;
        private final SaslAuthenticationFactory saslAuthenticationFactory;
        private final Pool<ByteBuffer> messageBufferPool;
        private final Pool<ByteBuffer> framingBufferPool;

        AcceptListener(final OptionMap serverOptionMap, final SaslAuthenticationFactory saslAuthenticationFactory) {
            this.serverOptionMap = serverOptionMap;
            this.saslAuthenticationFactory = saslAuthenticationFactory;
            final int messageBufferSize = defaultBufferSize;
            Pool<ByteBuffer> pool = USE_POOLING ? new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, messageBufferSize, messageBufferSize * 2) : Buffers.allocatedBufferPool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, messageBufferSize);
            messageBufferPool = LEAK_DEBUGGING ? new DebuggingBufferPool(pool) : pool;
            final int framingBufferSize = messageBufferSize + 4;
            pool = USE_POOLING ? new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, framingBufferSize, framingBufferSize * 2) : Buffers.allocatedBufferPool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, framingBufferSize);
            framingBufferPool = LEAK_DEBUGGING ? new DebuggingBufferPool(pool) : pool;
        }

        public void handleEvent(final AcceptingChannel<? extends ConnectedStreamChannel> channel) {
            final ConnectedStreamChannel accepted;
            try {
                accepted = channel.accept();
                if (accepted == null) {
                    return;
                }
            } catch (IOException e) {
                log.failedToAccept(e);
                return;
            }
            try {
                accepted.setOption(Options.TCP_NODELAY, Boolean.TRUE);
            } catch (IOException e) {
                // ignore
            }

            final FramedMessageChannel messageChannel = new FramedMessageChannel(accepted, framingBufferPool.allocate(), framingBufferPool.allocate());
            final RemoteConnection connection = new RemoteConnection(messageBufferPool, accepted, messageChannel, serverOptionMap, RemoteConnectionProvider.this);
            final ServerConnectionOpenListener openListener = new ServerConnectionOpenListener(connection, connectionProviderContext, saslAuthenticationFactory, serverOptionMap);
            messageChannel.getWriteSetter().set(connection.getWriteListener());
            RemoteLogger.log.tracef("Accepted connection from %s to %s", accepted.getPeerAddress(), accepted.getLocalAddress());
            openListener.handleEvent(messageChannel);
        }
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

    int getDefaultBufferSize() {
        return defaultBufferSize;
    }
}
