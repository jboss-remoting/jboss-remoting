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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReferenceArray;

import javax.security.sasl.SaslServerFactory;

import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.auth.provider.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.sasl.util.SaslMechanismInformation;
import org.wildfly.security.sasl.util.ServiceLoaderSaslServerFactory;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class ConnectionTestCase {

    protected final Endpoint clientEndpoint;
    protected final Endpoint serverEndpoint;

    private AcceptingChannel<? extends ConnectedStreamChannel> server;

    private static String providerName;

    @BeforeClass
    public static void doBeforeClass() {
        final WildFlyElytronProvider provider = new WildFlyElytronProvider();
        Security.addProvider(provider);
        providerName = provider.getName();
    }

    @AfterClass
    public static void doAfterClass() {
        Security.removeProvider(providerName);
    }

    public ConnectionTestCase() throws IOException {
        OptionMap optionMap = OptionMap.builder()
            .set(Options.WORKER_TASK_CORE_THREADS, THREAD_POOL_SIZE)
            .set(Options.WORKER_TASK_MAX_THREADS, THREAD_POOL_SIZE)
            .set(Options.WORKER_IO_THREADS, IO_THREAD_COUNT)
            .getMap();
        clientEndpoint = Endpoint.builder().setXnioWorkerOptions(optionMap).setEndpointName("connection-test-client").build();
        serverEndpoint = Endpoint.builder().setXnioWorkerOptions(optionMap).setEndpointName("connection-test-server").build();
    }

    @Rule
    public TestName name = new TestName();

    @Before
    public void before() throws Exception {
        System.gc();
        System.runFinalization();
        Logger.getLogger("TEST").infof("Running test %s", name.getMethodName());
        final NetworkServerProvider networkServerProvider = serverEndpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
        domainBuilder.addRealm("mainRealm", mainRealm);
        domainBuilder.setDefaultRealmName("mainRealm");
        final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
        mainRealm.setPasswordMap("bob", "clear-password", passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())));
        final SaslServerFactory saslServerFactory = new ServiceLoaderSaslServerFactory(getClass().getClassLoader());
        final SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder();
        builder.setSecurityDomain(domainBuilder.build());
        builder.setSaslServerFactory(saslServerFactory);
        builder.addMechanism(SaslMechanismInformation.Names.SCRAM_SHA_256, MechanismConfiguration.EMPTY);
        final SaslAuthenticationFactory saslAuthenticationFactory = builder.build();
        server = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123), OptionMap.EMPTY, saslAuthenticationFactory);
    }

    @After
    public void after() {
        IoUtils.safeClose(server);
        IoUtils.safeClose(clientEndpoint);
        IoUtils.safeClose(serverEndpoint);
        System.gc();
        System.runFinalization();
        Logger.getLogger("TEST").infof("Finished test %s", name.getMethodName());
    }

    private static final int IO_THREAD_COUNT = (int) (Runtime.getRuntime().availableProcessors() * 1.5);
    private static final int THREAD_POOL_SIZE = 100;
    private static final int CONNECTION_COUNT = 10;
    private static final int MESSAGE_COUNT = 8;
    private static final int CHANNEL_COUNT = 10;
    private static final int BUFFER_SIZE = 8192;
    private static final byte[] junkBuffer = new byte[BUFFER_SIZE];

    @Test
    @Ignore
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
            IoFuture<Connection> futureConnection = AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.EMPTY.useName("bob").usePassword("pass").allowSaslMechanisms("SCRAM-SHA-256")).run(new PrivilegedAction<IoFuture<Connection>>() {
                public IoFuture<Connection> run() {
                    try {
                        return clientEndpoint.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY);
                    } catch (IOException | URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            final Connection connection = futureConnection.get();
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

    @Test
    public void rejectUnknownService() throws Exception {
        final Connection connection = AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.EMPTY.useName("bob").usePassword("pass").allowSaslMechanisms("SCRAM-SHA-256")).run(new PrivilegedAction<Connection>() {
            public Connection run() {
                try {
                    return clientEndpoint.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY).get();
                } catch (IOException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        final IoFuture<Channel> channelFuture = connection.openChannel("unknown", OptionMap.EMPTY);
        try {
            channelFuture.get();
            fail();
        } catch (CancellationException e) {
            throw e;
        } catch (IOException e) {
            // ok
        }
    }

    private static final int MAX_SERVER_RECEIVE = 0x18000;
    private static final int MAX_SERVER_TRANSMIT = 0x14000;

    @Test
    public void testChannelOptions() throws Exception {
        serverEndpoint.registerService("test", new OpenListener() {
            @Override
            public void channelOpened(Channel channel) {
                //
                Assert.assertTrue(channel.getOption(RemotingOptions.RECEIVE_WINDOW_SIZE) <= MAX_SERVER_RECEIVE);
                Assert.assertTrue(channel.getOption(RemotingOptions.TRANSMIT_WINDOW_SIZE) <= MAX_SERVER_TRANSMIT);
            }

            @Override
            public void registrationTerminated() {
                //
            }
        }, OptionMap.create(RemotingOptions.RECEIVE_WINDOW_SIZE, MAX_SERVER_RECEIVE, RemotingOptions.TRANSMIT_WINDOW_SIZE, MAX_SERVER_TRANSMIT));

        final Connection connection = AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.EMPTY.useName("bob").usePassword("pass").allowSaslMechanisms("SCRAM-SHA-256")).run(new PrivilegedAction<Connection>() {
            public Connection run() {
                try {
                    return clientEndpoint.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY).get();
                } catch (IOException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        IoFuture<Channel> future = connection.openChannel("test", OptionMap.create(RemotingOptions.RECEIVE_WINDOW_SIZE, 0x8000, RemotingOptions.TRANSMIT_WINDOW_SIZE, 0x12000));
        Channel channel = future.get();
        try {
            Assert.assertEquals("transmit", 0x12000, (int) channel.getOption(RemotingOptions.TRANSMIT_WINDOW_SIZE));
            Assert.assertEquals("receive", 0x8000, (int) channel.getOption(RemotingOptions.RECEIVE_WINDOW_SIZE));
        } finally {
            if(channel != null) {
                channel.close();
            }
        }
        future = connection.openChannel("test", OptionMap.create(RemotingOptions.RECEIVE_WINDOW_SIZE, 0x24000, RemotingOptions.TRANSMIT_WINDOW_SIZE, 0x24000));
        channel = future.get();
        try {
            Assert.assertEquals("transmit", MAX_SERVER_RECEIVE, (int) channel.getOption(RemotingOptions.TRANSMIT_WINDOW_SIZE));
            Assert.assertEquals("receive", MAX_SERVER_TRANSMIT, (int) channel.getOption(RemotingOptions.RECEIVE_WINDOW_SIZE));
        } finally {
            if(channel != null) {
                channel.close();
            }
        }
    }


}
