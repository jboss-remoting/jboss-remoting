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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.xnio.IoUtils.safeClose;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslServerFactory;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
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
 * Test for remote channel communication.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemoteChannelTest extends ChannelTestBase {
    protected static Endpoint endpoint;
    private static Closeable streamServer;
    private Connection connection;
    private Registration serviceRegistration;


    @BeforeClass
    public static void create() throws Exception {
        endpoint = Endpoint.builder().setEndpointName("test").build();
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
        domainBuilder.addRealm("mainRealm", mainRealm).build();
        domainBuilder.setDefaultRealmName("mainRealm");
        domainBuilder.setPermissionMapper((permissionMappable, roles) -> PermissionVerifier.ALL);
        final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
        mainRealm.setPasswordMap("bob", passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())));
        final SaslServerFactory saslServerFactory = new ServiceLoaderSaslServerFactory(RemoteChannelTest.class.getClassLoader());
        final SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder();
        builder.setSecurityDomain(domainBuilder.build());
        builder.setFactory(saslServerFactory);
        builder.setMechanismConfigurationSelector(mechanismInformation -> SaslMechanismInformation.Names.SCRAM_SHA_256.equals(mechanismInformation.getMechanismName()) ? MechanismConfiguration.EMPTY : null);
        final SaslAuthenticationFactory saslAuthenticationFactory = builder.build();
        streamServer = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE), saslAuthenticationFactory, SSLContext.getDefault());
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
        assertNull("No SSLSession", connection.getSslSession());
        IoFuture<Channel> futureChannel = connection.openChannel("org.jboss.test", OptionMap.EMPTY);
        sendChannel = futureChannel.get();
        recvChannel = passer.getIoFuture().get();
        assertNotNull(recvChannel);
        assertNull("No SSLSession", recvChannel.getConnection().getSslSession());
//        assertEquals("bob",recvChannel.getConnection().getUserInfo().getUserName());
    }

    @After
    public void testFinish() {
        safeClose(sendChannel);
        safeClose(recvChannel);
        safeClose(connection);
        serviceRegistration.close();
    }

    @AfterClass
    public static void destroy() throws IOException, InterruptedException {
        safeClose(streamServer);
        safeClose(endpoint);
        //some environments seem to need a small delay to re-bind the socket
        // we could alternatively shutdown the xnio worker, this would guarantee
        // that all tasks were fully completely for closing, but the worker is not
        // reachable from outside the remoting api
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            //ignore
        }
    }

    @Test
    public void testRefused() throws Exception {
        IoFuture<Connection> futureConnection = endpoint.connect(new URI("remote://localhost:33123"), OptionMap.EMPTY);
        try {
            futureConnection.awaitInterruptibly(2L, TimeUnit.SECONDS);
            if (futureConnection.getStatus() == IoFuture.Status.WAITING) {
                futureConnection.cancel();
                return;
            } else {
                safeClose(futureConnection.get());
            }
        } catch (IOException expected) {
            System.out.println("Exception is: " + expected);
            System.out.flush();
            if (expected.getMessage().toLowerCase(Locale.US).contains("refused")) {
                return;
            }
        }
        fail("Expected an IOException with 'refused' in the string, future connection status is " + futureConnection.getStatus());
    }
}
