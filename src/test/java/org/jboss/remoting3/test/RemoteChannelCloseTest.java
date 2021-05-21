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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslServerFactory;

import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
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
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * Test that the channel {@link CloseHandler}s are invoked when the channel is closed for whatever reasons
 * <p/>
 * User: Jaikiran Pai
 */
public class RemoteChannelCloseTest {

    private static Endpoint endpoint;
    private static String providerName;
    private Channel clientChannel;
    private Channel serverChannel;

    private static Closeable streamServer;
    private Connection connection;
    private Registration serviceRegistration;

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
        final SaslServerFactory saslServerFactory = new ServiceLoaderSaslServerFactory(RemoteChannelCloseTest.class.getClassLoader());
        final SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder();
        builder.setSecurityDomain(domainBuilder.build());
        builder.setFactory(saslServerFactory);
        builder.setMechanismConfigurationSelector(mechanismInformation -> SaslMechanismInformation.Names.SCRAM_SHA_256.equals(mechanismInformation.getMechanismName()) ? MechanismConfiguration.EMPTY : null);
        final SaslAuthenticationFactory saslAuthenticationFactory = builder.build();
        streamServer = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE), saslAuthenticationFactory, SSLContext.getDefault());
    }

    @Rule
    public TestName name = new TestName();

    @Before
    public void testStart() throws IOException, URISyntaxException, InterruptedException {
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
                    return endpoint.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });
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
        System.gc();
        System.runFinalization();
        Logger.getLogger("TEST").infof("Finished test %s", name.getMethodName());
    }

    @AfterClass
    public static void destroy() throws IOException, InterruptedException {
        IoUtils.safeClose(streamServer);
        IoUtils.safeClose(endpoint);
        Security.removeProvider(providerName);
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
