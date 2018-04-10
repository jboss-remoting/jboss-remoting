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

import static org.junit.Assert.assertNotNull;
import static org.xnio.IoUtils.safeClose;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslServerFactory;

import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
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
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * Tests that a {@link org.jboss.remoting3.MessageOutputStream#close() closing the message on the channel}
 * synchronously decrements the outbound message count maintained by JBoss Remoting.
 *
 * @author Jaikiran Pai
 */
public class OutboundMessageCountTestCase {

    private static final Logger logger = Logger.getLogger(OutboundMessageCountTestCase.class);
    private static final int MAX_OUTBOUND_MESSAGES = 20;
    private static Endpoint endpoint;
    private Channel clientChannel;
    private Channel serverChannel;

    private static Closeable streamServer;
    private Connection connection;
    private Registration serviceRegistration;

    private static boolean ipV6Supported = true;

    @Rule
    public TestName name = new TestName();
    private static String providerName;

    @AfterClass
    public static void doAfterClass() {
        Security.removeProvider(providerName);
    }

    @BeforeClass
    public static void create() throws Exception {
        final WildFlyElytronProvider provider = new WildFlyElytronProvider();
        Security.addProvider(provider);
        providerName = provider.getName();
        endpoint = Endpoint.builder().setEndpointName("test").build();
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
        domainBuilder.addRealm("mainRealm", mainRealm).build();
        domainBuilder.setDefaultRealmName("mainRealm");
        domainBuilder.setPermissionMapper((permissionMappable, roles) -> PermissionVerifier.ALL);
        final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
        mainRealm.setPasswordMap("bob", passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())));
        final SaslServerFactory saslServerFactory = new ServiceLoaderSaslServerFactory(OutboundMessageCountTestCase.class.getClassLoader());
        final SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder();
        builder.setSecurityDomain(domainBuilder.build());
        builder.setFactory(saslServerFactory);
        builder.setMechanismConfigurationSelector(mechanismInformation -> SaslMechanismInformation.Names.SCRAM_SHA_256.equals(mechanismInformation.getMechanismName()) ? MechanismConfiguration.EMPTY : null);
        final SaslAuthenticationFactory saslAuthenticationFactory = builder.build();
        ipV6Supported = isIPV6Supported();
        streamServer = networkServerProvider.createServer(new InetSocketAddress(ipV6Supported ? "::1" : "localhost", 30123), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE), saslAuthenticationFactory, SSLContext.getDefault());
    }

    @Before
    public void beforeTest() throws IOException, URISyntaxException, InterruptedException {
        System.gc();
        System.runFinalization();
        Logger.getLogger("TEST").infof("Running test %s", name.getMethodName());
        final FutureResult<Channel> passer = new FutureResult<Channel>();
        serviceRegistration = endpoint.registerService("org.jboss.test", new OpenListener() {
            public void channelOpened(final Channel channel) {
                passer.setResult(channel);
            }

            public void registrationTerminated() {
            }
        }, OptionMap.EMPTY);
        IoFuture<Connection> futureConnection = AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.empty().useName("bob").usePassword("pass").setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("SCRAM-SHA-256"))).run(new PrivilegedAction<IoFuture<Connection>>() {
            public IoFuture<Connection> run() {
                try {
                    return endpoint.connect(new URI("remote://" + (ipV6Supported ? "[::1]" : "localhost") + ":30123"), OptionMap.EMPTY);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        connection = futureConnection.get();
        final OptionMap channelCreationOptions = OptionMap.create(RemotingOptions.MAX_OUTBOUND_MESSAGES, MAX_OUTBOUND_MESSAGES);
        IoFuture<Channel> futureChannel = connection.openChannel("org.jboss.test", channelCreationOptions);
        clientChannel = futureChannel.get();
        serverChannel = passer.getIoFuture().get();
        assertNotNull(serverChannel);

    }

    private static boolean isIPV6Supported() {
        try(Socket socket = new Socket()) {
            socket.bind(new InetSocketAddress("::1", 0));
            return true;
        } catch (IOException e) {
            logger.errorf(e, "IPV6 is not suuported");
            return false;
        }
    }

    @After
    public void afterTest() {
        safeClose(serverChannel);
        safeClose(clientChannel);
        safeClose(connection);
        serviceRegistration.close();
        System.gc();
        System.runFinalization();
        Logger.getLogger("TEST").infof("Finished test %s", name.getMethodName());
    }

    @AfterClass
    public static void destroy() throws IOException, InterruptedException {
        safeClose(streamServer);
        safeClose(endpoint);
    }

    /**
     * Tests that multiple threads opening and closing a message on the channel doesn't cause the JBoss Remoting
     * code to get out of sync with the current outbound message count it maintains.
     *
     * @throws Exception
     */
    @Test
    public void testOutboundMessageSend() throws Exception {
        serverChannel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {

            }

            public void handleEnd(final Channel channel) {

            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                channel.receiveMessage(this);
                try {
                    while (message.read() != -1);
                } catch (IOException ignored) {
                } finally {
                    safeClose(message);
                }
            }
        });
        final int NUM_THREADS = 150;
        final ExecutorService executorService = Executors.newFixedThreadPool(NUM_THREADS);
        final Future<Throwable>[] futureFailures = new Future[NUM_THREADS];
        final Semaphore semaphore = new Semaphore(MAX_OUTBOUND_MESSAGES, true);
        try {
            // create and submit the tasks which will send out the messages
            for (int i = 0; i < NUM_THREADS; i++) {
                futureFailures[i] = executorService.submit(new MessageSender(this.clientChannel, semaphore));
            }
            int failureCount = 0;
            // wait for the tasks to complete and then collect any failures
            for (int i = 0; i < NUM_THREADS; i++) {
                final Throwable failure = futureFailures[i].get();
                if (failure == null) {
                    continue;
                }
                failureCount++;
                logger.info("Thread#" + i + " failed with exception", failure);
            }
            Assert.assertEquals("Some threads failed to send message on the channel", 0, failureCount);
        } finally {
            executorService.shutdown();
        }
    }

    private class MessageSender implements Callable<Throwable> {

        private Semaphore semaphore;
        private Channel channel;

        MessageSender(final Channel channel, final Semaphore semaphore) {
            this.semaphore = semaphore;
            this.channel = channel;
        }

        @Override
        public Throwable call() throws Exception {
            for (int i = 0; i < 3; i++) {
                try {
                    // get a permit before trying to send a message to the channel
                    this.semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return e;
                }
                MessageOutputStream messageOutputStream = null;
                try {
                    // now send a message
                    messageOutputStream = this.channel.writeMessage();
                    messageOutputStream.write("hello".getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    return e;
                } finally {
                    // close the message
                    if (messageOutputStream != null) {
                        messageOutputStream.close();
                    }
                    // release the permit for others to use
                    this.semaphore.release();
                }
            }
            // no failures, return null
            return null;
        }
    }
}
