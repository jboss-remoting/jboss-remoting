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

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.security.SimpleServerAuthenticationProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.Xnio;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;

/**
 * Test that the channel {@link CloseHandler}s are invoked when the channel is closed for whatever reasons
 * <p/>
 * User: Jaikiran Pai
 */
public class RemoteChannelCloseTest {

    private static Endpoint endpoint;
    private Channel clientChannel;
    private Channel serverChannel;

    private static ExecutorService executorService;
    private static AcceptingChannel<? extends ConnectedStreamChannel> streamServer;
    private static Registration registration;
    private Connection connection;
    private Registration serviceRegistration;

    @BeforeClass
    public static void create() throws IOException {
        executorService = new ThreadPoolExecutor(16, 16, 1L, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>());
        endpoint = Remoting.createEndpoint("test", executorService, OptionMap.EMPTY);
        Xnio xnio = Xnio.getInstance();
        registration = endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(xnio), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        SimpleServerAuthenticationProvider provider = new SimpleServerAuthenticationProvider();
        provider.addUser("bob", "test", "pass".toCharArray());
        streamServer = networkServerProvider.createServer(new InetSocketAddress("::1", 30123), OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("CRAM-MD5")), provider);
    }

    @Before
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
        clientChannel = futureChannel.get();
        serverChannel = passer.getIoFuture().get();
        assertNotNull(serverChannel);

    }

    @After
    public void afterTest() {
        IoUtils.safeClose(clientChannel);
        IoUtils.safeClose(serverChannel);
        IoUtils.safeClose(connection);
        serviceRegistration.close();
    }

    @AfterClass
    public static void destroy() throws IOException, InterruptedException {
        IoUtils.safeClose(streamServer);
        IoUtils.safeClose(endpoint);
        IoUtils.safeClose(registration);
        executorService.shutdown();
        executorService.awaitTermination(1L, TimeUnit.DAYS);
        executorService.shutdownNow();
    }

    /**
     * Tests that when the client side of the channel closes and the channel communication is broken,
     * the server side channel {@link CloseHandler}s are notified
     *
     * @throws Exception
     */
    @Test
    public void testRemoteClose() throws Exception {
        // latch which will be used by the channel CloseHandler to let the world know that
        // the channel CloseHandler was invoked
        final CountDownLatch closeHandlerNotificationLatch = new CountDownLatch(1);
        final ChannelCloseHandler closeHandler = new ChannelCloseHandler(closeHandlerNotificationLatch);
        // add the close handler to the server side channel
        this.serverChannel.addCloseHandler(closeHandler);
        // close the client connection (and expect the server channel close handler to be notified)
        this.connection.close();
        // wait for a few seconds for the close handler notification (since the CloseHandler can be called
        // async)
        final boolean closeHandlerInvoked = closeHandlerNotificationLatch.await(5, TimeUnit.SECONDS);
        Assert.assertTrue("Channel close handler not invoked", closeHandlerInvoked);

    }

    private class ChannelCloseHandler implements CloseHandler<Channel> {

        private final CountDownLatch latch;

        ChannelCloseHandler(final CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void handleClose(Channel closed, IOException exception) {
            this.latch.countDown();
        }
    }
}
