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

package org.jboss.remoting3.test;

import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.security.SimpleServerAuthenticationProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.xnio.BufferAllocator;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.ChannelThreadPool;
import org.xnio.ChannelThreadPools;
import org.xnio.ConnectionChannelThread;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pool;
import org.xnio.ReadChannelThread;
import org.xnio.Sequence;
import org.xnio.WriteChannelThread;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemoteChannelTest extends ChannelTestBase {
    protected Endpoint endpoint;
    protected ExecutorService executorService;
    private ReadChannelThread readChannelThread;
    private WriteChannelThread writeChannelThread;
    private ConnectionChannelThread connectionChannelThread;
    private AcceptingChannel<? extends ConnectedStreamChannel> streamServer;
    private Connection connection;
    private Registration serviceRegistration;

    @BeforeClass
    public void create() throws IOException {
        executorService = new ThreadPoolExecutor(16, 16, 1L, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>());
        endpoint = Remoting.createEndpoint("test", executorService, OptionMap.EMPTY);
        Xnio xnio = Xnio.getInstance();
        readChannelThread = xnio.createReadChannelThread(Executors.defaultThreadFactory());
        writeChannelThread = xnio.createWriteChannelThread(Executors.defaultThreadFactory());
        connectionChannelThread = xnio.createConnectionChannelThread(Executors.defaultThreadFactory());
        ChannelThreadPool<ReadChannelThread> readPool = ChannelThreadPools.singleton(readChannelThread);
        ChannelThreadPool<WriteChannelThread> writePool = ChannelThreadPools.singleton(writeChannelThread);
        ChannelThreadPool<ConnectionChannelThread> connectionPool = ChannelThreadPools.singleton(connectionChannelThread);
        Pool<ByteBuffer> bufferPool = Buffers.allocatedBufferPool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 8192);
        endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(xnio, bufferPool, readPool, writePool, connectionPool));
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        SimpleServerAuthenticationProvider provider = new SimpleServerAuthenticationProvider();
        provider.addUser("bob", "test", "pass".toCharArray());
        ChannelListener<AcceptingChannel<ConnectedStreamChannel>> serverListener = networkServerProvider.getServerListener(OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("DIGEST-MD5")), provider);
        streamServer = xnio.createStreamServer(new InetSocketAddress("::1", 30123), connectionChannelThread, serverListener, OptionMap.EMPTY);
    }

    @BeforeMethod
    public void testStart() throws IOException, URISyntaxException, InterruptedException {
        final FutureResult<Channel> passer = new FutureResult<Channel>();
        serviceRegistration = endpoint.registerService("org.jboss.test", new OpenListener() {
            public void channelOpened(final Channel channel) {
                passer.setResult(channel);
            }

            public void registrationTerminated() {
            }
        }, OptionMap.EMPTY);
        IoFuture<Connection> futureConnection = endpoint.connect(new URI("remote://[::1]:30123"), OptionMap.EMPTY, "bob", "test", "pass".toCharArray());
        connection = futureConnection.get();
        IoFuture<Channel> futureChannel = connection.openChannel("org.jboss.test", OptionMap.EMPTY);
        sendChannel = futureChannel.get();
        recvChannel = passer.getIoFuture().get();
        assertNotNull(recvChannel);
    }

    @AfterMethod
    public void testFinish() {
        IoUtils.safeClose(sendChannel);
        IoUtils.safeClose(recvChannel);
        IoUtils.safeClose(connection);
        serviceRegistration.close();
    }

    @AfterClass
    public void destroy() throws IOException, InterruptedException {
        IoUtils.safeClose(streamServer);
        IoUtils.safeClose(endpoint);
        executorService.shutdown();
        executorService.awaitTermination(1L, TimeUnit.DAYS);
        executorService.shutdownNow();
        readChannelThread.shutdown();
        writeChannelThread.shutdown();
        connectionChannelThread.shutdown();
    }

    @Test
    public void testSimpleWriteMethod() throws Exception {
        super.testSimpleWriteMethod();
    }

    @Test
    public void testEmptyMessage() throws IOException, InterruptedException {
        super.testEmptyMessage();
    }

    @Test
    public void testLotsOfContent() throws IOException, InterruptedException {
        super.testLotsOfContent();
    }

    @Test
    public void testWriteCancel() throws IOException, InterruptedException {
        super.testWriteCancel();
    }
}
