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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.security.ServerAuthenticationProvider;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferSlicePool;
import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.ChannelThreadPool;
import org.xnio.ChannelThreadPools;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.ReadChannelThread;
import org.xnio.Result;
import org.xnio.WriteChannelThread;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.FramedMessageChannel;
import org.xnio.ssl.XnioSsl;

import javax.security.auth.callback.CallbackHandler;

import static org.jboss.remoting3.remote.RemoteLogger.log;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteConnectionProvider implements ConnectionProvider {

    private final ProviderInterface providerInterface = new ProviderInterface();
    private final Xnio xnio;
    private final XnioSsl xnioSsl;
    private final ChannelThreadPool<ReadChannelThread> readThreadPool;
    private final ChannelThreadPool<WriteChannelThread> writeThreadPool;
    private final ConnectionProviderContext connectionProviderContext;
    private final Pool<ByteBuffer> messageBufferPool;
    private final Pool<ByteBuffer> framingBufferPool;

    RemoteConnectionProvider(final Xnio xnio, final OptionMap optionMap, final ConnectionProviderContext connectionProviderContext) throws IOException {
        this.xnio = xnio;
        try {
            if (optionMap.get(Options.SSL_ENABLED, true)) {
                xnioSsl = xnio.getSslProvider(optionMap);
            } else {
                xnioSsl = null;
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to configure SSL", e);
        }
        readThreadPool = ChannelThreadPools.createLightestLoadPool();
        ChannelThreadPools.addReadThreadsToPool(xnio, readThreadPool, optionMap.get(RemotingOptions.READ_THREAD_POOL_SIZE, 1), optionMap);
        writeThreadPool = ChannelThreadPools.createLightestLoadPool();
        ChannelThreadPools.addWriteThreadsToPool(xnio, writeThreadPool, optionMap.get(RemotingOptions.WRITE_THREAD_POOL_SIZE, 1), optionMap);
        this.connectionProviderContext = connectionProviderContext;
        final int messageBufferSize = optionMap.get(RemotingOptions.RECEIVE_BUFFER_SIZE, 8192);
        messageBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, messageBufferSize, optionMap.get(RemotingOptions.BUFFER_REGION_SIZE, messageBufferSize * 2));
        final int framingBufferSize = messageBufferSize + 4;
        framingBufferPool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, framingBufferSize, optionMap.get(RemotingOptions.BUFFER_REGION_SIZE, framingBufferSize * 2));
    }

    public Cancellable connect(final URI uri, final OptionMap connectOptions, final Result<ConnectionHandlerFactory> result, final CallbackHandler callbackHandler) throws IllegalArgumentException {
        RemoteLogger.log.tracef("Attempting to connect to \"%s\" with options %s", uri, connectOptions);
        final boolean sslCapable = xnioSsl != null;
        boolean useSsl = sslCapable && ! connectOptions.get(Options.SECURE, false);
        final InetSocketAddress destination;
        try {
            destination = new InetSocketAddress(InetAddress.getByName(uri.getHost()), uri.getPort());
        } catch (UnknownHostException e) {
            result.setException(e);
            return IoUtils.nullCancellable();
        }
        ChannelListener<ConnectedStreamChannel> openListener = new ChannelListener<ConnectedStreamChannel>() {
            public void handleEvent(final ConnectedStreamChannel channel) {
                try {
                    channel.setOption(Options.TCP_NODELAY, Boolean.TRUE);
                } catch (IOException e) {
                    // ignore
                }
                final FramedMessageChannel messageChannel = new FramedMessageChannel(channel, framingBufferPool.allocate(), framingBufferPool.allocate());
                final RemoteConnection remoteConnection = new RemoteConnection(messageBufferPool, channel, messageChannel, connectOptions, connectionProviderContext.getExecutor());
                remoteConnection.setResult(result);
                messageChannel.getWriteSetter().set(remoteConnection.getWriteListener());
                final ClientConnectionOpenListener openListener = new ClientConnectionOpenListener(remoteConnection, callbackHandler, AccessController.getContext(), connectOptions);
                openListener.handleEvent(messageChannel);
            }
        };
        final WriteChannelThread writeThread = writeThreadPool.getThread();
        final ReadChannelThread readThread = readThreadPool.getThread();
        if (useSsl) {
            return xnioSsl.connectSsl(destination, writeThread, readThread, writeThread, openListener, connectOptions);
        } else {
            return xnio.connectStream(destination, writeThread, readThread, writeThread, openListener, connectOptions);
        }
    }

    public Object getProviderInterface() {
        return providerInterface;
    }

    public void close() {
        ChannelThreadPools.shutdown(readThreadPool);
        ChannelThreadPools.shutdown(writeThreadPool);
    }

    final class ProviderInterface implements NetworkServerProvider {

        public AcceptingChannel<? extends ConnectedStreamChannel> createServer(final SocketAddress bindAddress, final OptionMap optionMap, final ServerAuthenticationProvider authenticationProvider) throws IOException {
            final boolean sslCapable = xnioSsl != null;
            final WriteChannelThread writeThread = writeThreadPool.getThread();
            final AcceptListener acceptListener = new AcceptListener(optionMap, authenticationProvider);
            if (sslCapable && optionMap.get(Options.SSL_ENABLED, true)) {
                return xnioSsl.createSslTcpServer((InetSocketAddress) bindAddress, writeThread, acceptListener, optionMap);
            } else {
                return xnio.createStreamServer(bindAddress, writeThread, acceptListener, optionMap);
            }
        }
    }

    private final class AcceptListener implements ChannelListener<AcceptingChannel<? extends ConnectedStreamChannel>> {

        private final OptionMap serverOptionMap;
        private final ServerAuthenticationProvider serverAuthenticationProvider;

        AcceptListener(final OptionMap serverOptionMap, final ServerAuthenticationProvider serverAuthenticationProvider) {
            this.serverOptionMap = serverOptionMap;
            this.serverAuthenticationProvider = serverAuthenticationProvider;
        }

        public void handleEvent(final AcceptingChannel<? extends ConnectedStreamChannel> channel) {
            ConnectedStreamChannel accepted = null;
            try {
                accepted = channel.accept(readThreadPool.getThread(), writeThreadPool.getThread());
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
            final RemoteConnection connection = new RemoteConnection(messageBufferPool, accepted, messageChannel, serverOptionMap, connectionProviderContext.getExecutor());
            final ServerConnectionOpenListener openListener = new ServerConnectionOpenListener(connection, connectionProviderContext, serverAuthenticationProvider, serverOptionMap);
            messageChannel.getWriteSetter().set(connection.getWriteListener());
            RemoteLogger.log.tracef("Accepted connection from %s to %s", accepted.getPeerAddress(), accepted.getLocalAddress());
            openListener.handleEvent(messageChannel);
        }
    }
}
