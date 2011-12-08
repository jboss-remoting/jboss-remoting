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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.security.ServerAuthenticationProvider;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.xnio.BufferAllocator;
import org.xnio.Buffers;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.Result;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.FramedMessageChannel;
import org.xnio.ssl.XnioSsl;

import javax.security.auth.callback.CallbackHandler;

import static org.jboss.remoting3.remote.RemoteLogger.log;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteConnectionProvider extends AbstractHandleableCloseable<ConnectionProvider> implements ConnectionProvider {

    private final ProviderInterface providerInterface = new ProviderInterface();
    private final Xnio xnio;
    private final XnioWorker xnioWorker;
    private final ConnectionProviderContext connectionProviderContext;
    private final Pool<ByteBuffer> messageBufferPool;
    private final Pool<ByteBuffer> framingBufferPool;
    private final boolean sslEnabled;

    RemoteConnectionProvider(final OptionMap optionMap, final ConnectionProviderContext connectionProviderContext) throws IOException {
        super(connectionProviderContext.getExecutor());
        xnio = connectionProviderContext.getXnio();
        sslEnabled = optionMap.get(Options.SSL_ENABLED, true);
        xnioWorker = connectionProviderContext.getXnioWorker();
        this.connectionProviderContext = connectionProviderContext;
        final int messageBufferSize = optionMap.get(RemotingOptions.RECEIVE_BUFFER_SIZE, 8192);
        messageBufferPool = false ? new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, messageBufferSize, optionMap.get(RemotingOptions.BUFFER_REGION_SIZE, messageBufferSize * 2)) : Buffers.allocatedBufferPool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, messageBufferSize);
        final int framingBufferSize = messageBufferSize + 4;
        framingBufferPool = false ? new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, framingBufferSize, optionMap.get(RemotingOptions.BUFFER_REGION_SIZE, framingBufferSize * 2)) : Buffers.allocatedBufferPool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, framingBufferSize);
    }

    public Cancellable connect(final SocketAddress bindAddress, final SocketAddress destination, final OptionMap connectOptions, final Result<ConnectionHandlerFactory> result, final CallbackHandler callbackHandler, XnioSsl xnioSsl) throws IllegalArgumentException {
        if (! isOpen()) {
            throw new IllegalStateException("Connection provider is closed");
        }
        if (destination == null) {
            throw new IllegalArgumentException("destination address may not be null");
        }
        if (bindAddress != null && destination != null && bindAddress.getClass() != destination.getClass()) {
            throw new IllegalArgumentException("bind and destination addresses must be of the same type");
        }
        log.tracef("Attempting to connect to \"%s\" with options %s", destination, connectOptions);
        final boolean sslCapable = sslEnabled;
        boolean useSsl = sslCapable && connectOptions.get(Options.SSL_ENABLED, true) && !connectOptions.get(Options.SECURE, false);
        ChannelListener<ConnectedStreamChannel> openListener = new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                try {
                    channel.setOption(Options.TCP_NODELAY, Boolean.TRUE);
                } catch (IOException e) {
                    // ignore
                }
                final FramedMessageChannel messageChannel = new FramedMessageChannel(channel, framingBufferPool.allocate(), framingBufferPool.allocate());
                final RemoteConnection remoteConnection = new RemoteConnection(messageBufferPool, channel, messageChannel, connectOptions, getExecutor());
                remoteConnection.setResult(result);
                messageChannel.getWriteSetter().set(remoteConnection.getWriteListener());
                final ClientConnectionOpenListener openListener = new ClientConnectionOpenListener(remoteConnection, connectionProviderContext, callbackHandler, AccessController.getContext(), connectOptions);
                openListener.handleEvent(messageChannel);
            }
        };
        final IoFuture<? extends ConnectedStreamChannel> future;
        if (useSsl && destination instanceof InetSocketAddress) {
            if (xnioSsl == null) {
                try {
                    xnioSsl = xnio.getSslProvider(connectOptions);
                } catch (GeneralSecurityException e) {
                    result.setException(sslConfigFailure(e));
                    return IoUtils.nullCancellable();
                }
            }
            future = bindAddress == null ? xnioSsl.connectSsl(xnioWorker, (InetSocketAddress) destination, openListener, connectOptions) : xnioSsl.connectSsl(xnioWorker, (InetSocketAddress) bindAddress, (InetSocketAddress) destination, openListener, connectOptions);
        } else {
            future = bindAddress == null ? xnioWorker.connectStream(destination, openListener, connectOptions) : xnioWorker.connectStream(bindAddress, destination, openListener, null, connectOptions);
        }
        future.addNotifier(new IoFuture.HandlingNotifier<ConnectedStreamChannel, Result<ConnectionHandlerFactory>>() {
            public void handleCancelled(final Result<ConnectionHandlerFactory> attachment) {
                attachment.setCancelled();
            }

            public void handleFailed(final IOException exception, final Result<ConnectionHandlerFactory> attachment) {
                attachment.setException(exception);
            }
        }, result);
        return future;
    }

    public Object getProviderInterface() {
        return providerInterface;
    }

    protected void closeAction() {
        closeComplete();
    }

    final class ProviderInterface implements NetworkServerProvider {

        public AcceptingChannel<? extends ConnectedStreamChannel> createServer(final SocketAddress bindAddress, final OptionMap optionMap, final ServerAuthenticationProvider authenticationProvider, XnioSsl xnioSsl) throws IOException {
            final AccessControlContext accessControlContext = AccessController.getContext();
            final boolean sslCapable = sslEnabled;
            final AcceptListener acceptListener = new AcceptListener(optionMap, authenticationProvider, accessControlContext);
            final AcceptingChannel<? extends ConnectedStreamChannel> result;
            if (sslCapable && optionMap.get(Options.SSL_ENABLED, true)) {
                if (xnioSsl == null) {
                    try {
                        xnioSsl = xnio.getSslProvider(optionMap);
                    } catch (GeneralSecurityException e) {
                        throw sslConfigFailure(e);
                    }
                }
                result = xnioSsl.createSslTcpServer(xnioWorker, (InetSocketAddress) bindAddress, acceptListener, optionMap);
            } else {
                result = xnioWorker.createStreamServer(bindAddress, acceptListener, optionMap);
            }
            addCloseHandler(new CloseHandler<ConnectionProvider>() {
                public void handleClose(final ConnectionProvider closed, final IOException exception) {
                    IoUtils.safeClose(result);
                }
            });
            result.resumeAccepts();
            return result;
        }
    }

    private static IOException sslConfigFailure(final GeneralSecurityException e) {
        return new IOException("Failed to configure SSL", e);
    }

    private final class AcceptListener implements ChannelListener<AcceptingChannel<? extends ConnectedStreamChannel>> {

        private final OptionMap serverOptionMap;
        private final ServerAuthenticationProvider serverAuthenticationProvider;
        private final AccessControlContext accessControlContext;

        AcceptListener(final OptionMap serverOptionMap, final ServerAuthenticationProvider serverAuthenticationProvider, final AccessControlContext accessControlContext) {
            this.serverOptionMap = serverOptionMap;
            this.serverAuthenticationProvider = serverAuthenticationProvider;
            this.accessControlContext = accessControlContext;
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
            final RemoteConnection connection = new RemoteConnection(messageBufferPool, accepted, messageChannel, serverOptionMap, getExecutor());
            final ServerConnectionOpenListener openListener = new ServerConnectionOpenListener(connection, connectionProviderContext, serverAuthenticationProvider, serverOptionMap, accessControlContext);
            messageChannel.getWriteSetter().set(connection.getWriteListener());
            RemoteLogger.log.tracef("Accepted connection from %s to %s", accepted.getPeerAddress(), accepted.getLocalAddress());
            openListener.handleEvent(messageChannel);
        }
    }

    public String toString() {
        return String.format("Remoting remote connection provider %x for %s", Integer.valueOf(hashCode()), connectionProviderContext.getEndpoint());
    }
}
