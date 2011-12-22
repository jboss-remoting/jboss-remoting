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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.security.SimpleServerAuthenticationProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class ConnectionTestCase {

    protected final Endpoint clientEndpoint;
    protected final Endpoint serverEndpoint;
    private final Registration clientReg;
    private final Registration serverReg;

    private AcceptingChannel<? extends ConnectedStreamChannel> server;

    public ConnectionTestCase() throws IOException {
        clientEndpoint = Remoting.createEndpoint("connection-test-client", OptionMap.create(Options.WORKER_TASK_CORE_THREADS, THREAD_POOL_SIZE, Options.WORKER_TASK_MAX_THREADS, THREAD_POOL_SIZE));
        serverEndpoint = Remoting.createEndpoint("connection-test-server", OptionMap.create(Options.WORKER_TASK_CORE_THREADS, THREAD_POOL_SIZE, Options.WORKER_TASK_MAX_THREADS, THREAD_POOL_SIZE));
        clientReg = clientEndpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        serverReg = serverEndpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
    }

    @Before
    public void before() throws IOException {
        final NetworkServerProvider networkServerProvider = serverEndpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        SimpleServerAuthenticationProvider provider = new SimpleServerAuthenticationProvider();
        provider.addUser("bob", "test", "pass".toCharArray());
        server = networkServerProvider.createServer(new InetSocketAddress("::1", 30123), OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("CRAM-MD5")), provider, null);
    }

    @After
    public void after() {
        IoUtils.safeClose(server);
        IoUtils.safeClose(clientReg);
        IoUtils.safeClose(serverReg);
        IoUtils.safeClose(clientEndpoint);
        IoUtils.safeClose(serverEndpoint);
    }

    private static final int THREAD_POOL_SIZE = 100;
    private static final int CONNECTION_COUNT = 20;
    private static final int MESSAGE_COUNT = 15;
    private static final int CHANNEL_COUNT = 30;
    private static final int BUFFER_SIZE = 8192;
    private static final byte[] junkBuffer = new byte[BUFFER_SIZE];

    @Test
    public void testManyChannelsLotsOfData() throws Exception {
        final XnioWorker clientWorker = clientEndpoint.getXnioWorker();
        final XnioWorker serverWorker = serverEndpoint.getXnioWorker();
        final Queue<Throwable> problems = new ConcurrentLinkedQueue<Throwable>();
        final CountDownLatch serverChannelCount = new CountDownLatch(CHANNEL_COUNT * CONNECTION_COUNT);
        final CountDownLatch clientChannelCount = new CountDownLatch(CHANNEL_COUNT * CONNECTION_COUNT);
        serverEndpoint.registerService("test", new OpenListener() {
            public void channelOpened(final Channel channel) {
                channel.receiveMessage(new Channel.Receiver() {
                    public void handleError(final Channel channel, final IOException error) {
                        problems.add(error);
                        error.printStackTrace();
                        serverChannelCount.countDown();
                    }

                    public void handleEnd(final Channel channel) {
                        serverChannelCount.countDown();
                    }

                    public void handleMessage(final Channel channel, final MessageInputStream message) {
                        try {
                            channel.receiveMessage(this);
                            while (message.read(junkBuffer) > -1);
                        } catch (Exception e) {
                            e.printStackTrace();
                            problems.add(e);
                        } finally {
                            IoUtils.safeClose(message);
                        }
                    }
                });
            }

            public void registrationTerminated() {
            }
        }, OptionMap.EMPTY);
        final AtomicReferenceArray<Connection> connections = new AtomicReferenceArray<Connection>(CONNECTION_COUNT);
        for (int h = 0; h < CONNECTION_COUNT; h ++) {
            final Connection connection = clientEndpoint.connect("remote", new InetSocketAddress("::1", 0), new InetSocketAddress("::1", 30123), OptionMap.EMPTY, "bob", "test", "pass".toCharArray()).get();
            connections.set(h, connection);
            for (int i = 0; i < CHANNEL_COUNT; i ++) {
                clientWorker.execute(new Runnable() {
                    public void run() {
                        final Random random = new Random();
                        final IoFuture<Channel> future = connection.openChannel("test", OptionMap.EMPTY);
                        try {
                            final Channel channel = future.get();
                            try {
                                final byte[] bytes = new byte[BUFFER_SIZE];
                                for (int j = 0; j < MESSAGE_COUNT; j++) {
                                    final MessageOutputStream stream = channel.writeMessage();
                                    try {
                                        for (int k = 0; k < 100; k++) {
                                            random.nextBytes(bytes);
                                            stream.write(bytes, 0, random.nextInt(BUFFER_SIZE - 1) + 1);
                                        }
                                        stream.close();
                                    } finally {
                                        IoUtils.safeClose(stream);
                                    }
                                    stream.close();
                                }
                            } finally {
                                IoUtils.safeClose(channel);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            problems.add(e);
                        } finally {
                            clientChannelCount.countDown();
                        }
                    }
                });
            }
        }
        Thread.sleep(500);
        serverChannelCount.await();
        clientChannelCount.await();
        for (int h = 0; h < CONNECTION_COUNT; h ++) {
            connections.get(h).close();
        }
        assertArrayEquals(new Object[0], problems.toArray());
    }

}
