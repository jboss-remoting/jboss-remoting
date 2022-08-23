/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.ProtocolException;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
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
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.XnioWorker;

import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslServerFactory;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests that the max inbound channels specified in default option map of EndPoint is honored.
 *
 * @author <a href="mailto:aoingl@gmail.com">Lin Gao</a>
 */
public class MaxChannelsTestCase {

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

    public MaxChannelsTestCase() throws IOException {
        clientEndpoint = Endpoint.builder().setDefaultConnectionsOptionMap(optionMap()).setEndpointName("connection-test-client").build();
        serverEndpoint = Endpoint.builder().setEndpointName("connection-test-server").build();
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
        server = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123), optionMap(), saslAuthenticationFactory, SSLContext.getDefault());
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

    private int getMaxInboundChannelsCount() {
        return 50;
    }

    private OptionMap optionMap() {
        return OptionMap.builder()
                .set(RemotingOptions.MAX_INBOUND_CHANNELS, getMaxInboundChannelsCount())
                .set(RemotingOptions.MAX_OUTBOUND_CHANNELS, getMaxInboundChannelsCount())
                .set(Options.SSL_ENABLED, Boolean.FALSE)
                .getMap();
    }

    @Test
    public void testMaxInboundChannels() throws Exception {
        final XnioWorker clientWorker = clientEndpoint.getXnioWorker();
        final Queue<Throwable> problems = new ConcurrentLinkedQueue<Throwable>();
        final AtomicReferenceArray<Channel> clientChannels = new AtomicReferenceArray<>(getMaxInboundChannelsCount());
        final CountDownLatch clientChannelCount = new CountDownLatch(getMaxInboundChannelsCount() + 1);
        final AtomicInteger openedChannels = new AtomicInteger(0);
        serverEndpoint.registerService("test", new OpenListener() {
            public void channelOpened(final Channel channel) {
                Logger.getLogger("TEST").infof("Channel counts: %s", openedChannels.incrementAndGet());
            }
            public void registrationTerminated() {
            }
        }, OptionMap.EMPTY);
        IoFuture<Connection> futureConnection = AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.empty().useName("bob").usePassword("pass").setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("SCRAM-SHA-256"))).run(new PrivilegedAction<IoFuture<Connection>>() {
            public IoFuture<Connection> run() {
                try {
                    // use empty option, the default option map defined in client endpoint will be respected
                    return clientEndpoint.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        final Connection connection = futureConnection.get();
        clientWorker.execute(() -> {
            for (int i = 0; i < getMaxInboundChannelsCount(); i ++) {
                final IoFuture<Channel> future = connection.openChannel("test", OptionMap.EMPTY);
                try {
                    clientChannels.set(i, future.get());
                } catch (Exception e) {
                    problems.add(e);
                } finally {
                    clientChannelCount.countDown();
                }
            }
            // next should fail
            try {
                connection.openChannel("test", OptionMap.EMPTY).get();
                fail("channel should not be opened");
            } catch (Exception e) {
                assertEquals(e.getClass(), ProtocolException.class);
                assertEquals("Too many channels open", e.getMessage());
            } finally {
                clientChannelCount.countDown();
            }
        });
        clientChannelCount.await();
        for (int h = 0; h < getMaxInboundChannelsCount(); h ++) {
            clientChannels.get(h).close();
        }
        assertArrayEquals(new Object[0], problems.toArray());
    }

}
