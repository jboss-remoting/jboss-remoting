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

package org.jboss.remoting3.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import java.io.Closeable;
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

import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslServerFactory;

import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.EndpointBuilder;
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
import org.wildfly.security.auth.realm.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.permission.PermissionVerifier;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.wildfly.security.sasl.util.SaslMechanismInformation;
import org.wildfly.security.sasl.util.ServiceLoaderSaslServerFactory;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class ConnectionTestCase {

    protected final Endpoint clientEndpoint;
    protected final Endpoint serverEndpoint;

    private Closeable server;

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
        final EndpointBuilder endpointBuilder = Endpoint.builder();
        final XnioWorker.Builder workerBuilder = endpointBuilder.buildXnioWorker(Xnio.getInstance());
        workerBuilder.setCoreWorkerPoolSize(THREAD_POOL_SIZE).setMaxWorkerPoolSize(THREAD_POOL_SIZE).setWorkerIoThreads(IO_THREAD_COUNT);
        endpointBuilder.setEndpointName("connection-test-client");
        clientEndpoint = endpointBuilder.build();
        endpointBuilder.setEndpointName("connection-test-server");
        serverEndpoint = endpointBuilder.build();
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
        domainBuilder.addRealm("mainRealm", mainRealm).build();
        domainBuilder.setDefaultRealmName("mainRealm");
        final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
        mainRealm.setPasswordMap("bob", passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())));
        final SaslServerFactory saslServerFactory = new ServiceLoaderSaslServerFactory(getClass().getClassLoader());
        final SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder();
        domainBuilder.setPermissionMapper((permissionMappable, roles) -> PermissionVerifier.ALL);
        builder.setSecurityDomain(domainBuilder.build());
        builder.setFactory(saslServerFactory);
        builder.setMechanismConfigurationSelector(mechanismInformation -> SaslMechanismInformation.Names.SCRAM_SHA_256.equals(mechanismInformation.getMechanismName()) ? MechanismConfiguration.EMPTY : null);
        final SaslAuthenticationFactory saslAuthenticationFactory = builder.build();
        server = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE), saslAuthenticationFactory, SSLContext.getDefault());
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
            IoFuture<Connection> futureConnection = AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.EMPTY.useName("bob").usePassword("pass").setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("SCRAM-SHA-256"))).run(new PrivilegedAction<IoFuture<Connection>>() {
                public IoFuture<Connection> run() {
                    try {
                        return clientEndpoint.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY);
                    } catch (URISyntaxException e) {
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
        final Connection connection = AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.EMPTY.useName("bob").usePassword("pass").setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("SCRAM-SHA-256"))).run(new PrivilegedAction<Connection>() {
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

        final Connection connection = AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.EMPTY.useName("bob").usePassword("pass").setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("SCRAM-SHA-256"))).run(new PrivilegedAction<Connection>() {
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
