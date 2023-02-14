/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package org.jboss.remoting3.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.security.Security;

import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslServerFactory;

import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.remote.RemoteConnection.RemoteWriteListener;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.jboss.remoting3.test.Utils;
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
import org.wildfly.security.auth.server.sasl.SaslAuthenticationFactory;
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
 * Tests the heartbeat option.
 *
 * @author tmiyar
 *
 */
public class HeartbeatTestCase {

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

    @Before
    public void doBefore() {
        System.gc();
        System.runFinalization();
        Logger.getLogger("TEST").infof("Running test %s", name.getMethodName());
    }

    @After
    public void doAfter() {
        System.gc();
        System.runFinalization();
        Logger.getLogger("TEST").infof("Finished test %s", name.getMethodName());
    }

    @Rule
    public TestName name = new TestName();

    private void afterTest(Channel clientChannel, Channel serverChannel, Connection connection, Registration serviceRegistration) {
        IoUtils.safeClose(clientChannel);
        IoUtils.safeClose(serverChannel);
        IoUtils.safeClose(connection);
        serviceRegistration.close();
    }

    private void destroy(Endpoint endpoint, Closeable streamServer) throws IOException, InterruptedException {
        IoUtils.safeClose(streamServer);
        IoUtils.safeClose(endpoint);
    }

    /**
     * Test that heartbeat can be set and can be disabled by setting it to 0
     *
     * @throws Exception
     */
    @Test
    public void testDisableHeartbeat() throws Exception {

        Channel clientChannel = null;
        Channel serverChannel = null;

        Closeable streamServer = null;
        Connection connection = null;
        Registration serviceRegistration = null;

        final Endpoint endpoint = Endpoint.builder().setEndpointName("test").build();
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote",
                NetworkServerProvider.class);
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
        domainBuilder.addRealm("mainRealm", mainRealm).build();
        domainBuilder.setDefaultRealmName("mainRealm");
        domainBuilder.setPermissionMapper((permissionMappable, roles) -> PermissionVerifier.ALL);
        final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
        mainRealm.setPasswordMap("bob", passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())));
        final SaslServerFactory saslServerFactory = new ServiceLoaderSaslServerFactory(
                HeartbeatTestCase.class.getClassLoader());
        final SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder();
        builder.setSecurityDomain(domainBuilder.build());
        builder.setFactory(saslServerFactory);
        builder.setMechanismConfigurationSelector(mechanismInformation -> SaslMechanismInformation.Names.SCRAM_SHA_256
                .equals(mechanismInformation.getMechanismName()) ? MechanismConfiguration.EMPTY : null);
        final SaslAuthenticationFactory saslAuthenticationFactory = builder.build();
        streamServer = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123),
                OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE), saslAuthenticationFactory, SSLContext.getDefault());

        final FutureResult<Channel> passer = new FutureResult<Channel>();
        serviceRegistration = endpoint.registerService("org.jboss.test", new OpenListener() {
            public void channelOpened(final Channel channel) {
                passer.setResult(channel);
            }

            public void registrationTerminated() {
            }
        }, OptionMap.EMPTY);
        IoFuture<Connection> futureConnection = AuthenticationContext.empty()
                .with(MatchRule.ALL,
                        AuthenticationConfiguration.empty().useName("bob").usePassword("pass")
                                .setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("SCRAM-SHA-256")))
                .run(new PrivilegedAction<IoFuture<Connection>>() {
                    public IoFuture<Connection> run() {
                        try {
                            return endpoint.connect(new URI("remote://localhost:30123"),
                                    OptionMap.create(RemotingOptions.HEARTBEAT_INTERVAL, 0));
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

        RemoteConnectionChannel remoteClientChannel = (RemoteConnectionChannel) clientChannel;
        assertEquals(0, Utils.getInstanceValue(remoteClientChannel.getRemoteConnection(), "heartbeatInterval"));
        RemoteWriteListener clientWriteListener = (RemoteWriteListener) Utils
                .getInstanceValue(remoteClientChannel.getRemoteConnection(), "writeListener");
        assertNull(Utils.getInstanceValue(clientWriteListener, "heartKey"));

        afterTest(clientChannel, serverChannel, connection, serviceRegistration);
        destroy(endpoint, streamServer);
    }

    /**
     * Test that heartbeat can be set and can be disabled by setting it to 0
     *
     * @throws Exception
     */
    @Test
    public void testDefaultHeartbeat() throws Exception {

        Channel clientChannel = null;
        Channel serverChannel = null;

        Closeable streamServer = null;
        Connection connection = null;
        Registration serviceRegistration = null;

        final Endpoint endpoint = Endpoint.builder().setEndpointName("test").build();
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote",
                NetworkServerProvider.class);
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
        domainBuilder.addRealm("mainRealm", mainRealm).build();
        domainBuilder.setDefaultRealmName("mainRealm");
        domainBuilder.setPermissionMapper((permissionMappable, roles) -> PermissionVerifier.ALL);
        final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
        mainRealm.setPasswordMap("bob", passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())));
        final SaslServerFactory saslServerFactory = new ServiceLoaderSaslServerFactory(
                HeartbeatTestCase.class.getClassLoader());
        final SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder();
        builder.setSecurityDomain(domainBuilder.build());
        builder.setFactory(saslServerFactory);
        builder.setMechanismConfigurationSelector(mechanismInformation -> SaslMechanismInformation.Names.SCRAM_SHA_256
                .equals(mechanismInformation.getMechanismName()) ? MechanismConfiguration.EMPTY : null);
        final SaslAuthenticationFactory saslAuthenticationFactory = builder.build();
        streamServer = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123),
                OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE), saslAuthenticationFactory, SSLContext.getDefault());

        final FutureResult<Channel> passer = new FutureResult<Channel>();
        serviceRegistration = endpoint.registerService("org.jboss.test", new OpenListener() {
            public void channelOpened(final Channel channel) {
                passer.setResult(channel);
            }

            public void registrationTerminated() {
            }
        }, OptionMap.EMPTY);
        IoFuture<Connection> futureConnection = AuthenticationContext.empty()
                .with(MatchRule.ALL,
                        AuthenticationConfiguration.empty().useName("bob").usePassword("pass")
                                .setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("SCRAM-SHA-256")))
                .run(new PrivilegedAction<IoFuture<Connection>>() {
                    public IoFuture<Connection> run() {
                        try {
                            return endpoint.connect(new URI("remote://localhost:30123"),
                                    OptionMap.EMPTY);
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

        RemoteConnectionChannel remoteClientChannel = (RemoteConnectionChannel) clientChannel;
        assertEquals(RemotingOptions.DEFAULT_HEARTBEAT_INTERVAL, Utils.getInstanceValue(remoteClientChannel.getRemoteConnection(), "heartbeatInterval"));
        RemoteWriteListener clientWriteListener = (RemoteWriteListener) Utils
                .getInstanceValue(remoteClientChannel.getRemoteConnection(), "writeListener");
        assertNotNull(Utils.getInstanceValue(clientWriteListener, "heartKey"));

        afterTest(clientChannel, serverChannel, connection, serviceRegistration);
        destroy(endpoint, streamServer);
    }

}
