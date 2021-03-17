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

import org.jboss.logging.Logger;
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

import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslServerFactory;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.xnio.IoUtils.safeClose;

/**
 * Test for creation of channels for services which have validationPredicates specified.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:rachmato@redhat.com">Richard Achmatowicz</a>
 */
public final class RemoteServiceWithPredicateTest  {
    protected static Endpoint endpoint;
    private static Closeable streamServer;
    private static String providerName ;

    /**
     * Create an Endpoint and an AcceptingChannel<StreamConnection> to receive connection requests
     * @throws Exception
     */
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
        final SaslServerFactory saslServerFactory = new ServiceLoaderSaslServerFactory(RemoteServiceWithPredicateTest.class.getClassLoader());
        final SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder();
        builder.setSecurityDomain(domainBuilder.build());
        builder.setFactory(saslServerFactory);
        builder.setMechanismConfigurationSelector(mechanismInformation -> SaslMechanismInformation.Names.SCRAM_SHA_256.equals(mechanismInformation.getMechanismName()) ? MechanismConfiguration.EMPTY : null);
        final SaslAuthenticationFactory saslAuthenticationFactory = builder.build();
        streamServer = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE), saslAuthenticationFactory, SSLContext.getDefault());
    }

    @AfterClass
    public static void destroy() throws IOException, InterruptedException {
        safeClose(streamServer);
        safeClose(endpoint);
        Security.removeProvider(providerName);
    }

    @Rule
    public TestName name = new TestName();

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

    /**
     * Test which verifies that a validationPredicate evaluating to true will accept creation of service Channels
     *
     * @throws IOException
     */
    @Test
    public void testAcceptedByPredicate() throws IOException {
        // the validation predicate
        final Predicate<Connection> validationPredicate = connection -> true;
        tryToConnectToService(validationPredicate, true);
    }

    /**
     * Test which verifies that a validationPredicate evaluating to false will prohibit creation of service Channels
     *
     * @throws IOException
     */
    @Test
    public void testRefusedByPredicate() throws IOException {
        // the validation predicate
        final Predicate<Connection> validationPredicate = connection -> false;
        tryToConnectToService(validationPredicate, false);
    }

    /**
     * Method which registers a service with the provided validationPredicate, then tests to see if the service
     * accepts or rejects service Channel creation attempts which are expected by the predicate provided.
     *
     * @param validationPredicate
     * @throws IOException
     * @throws URISyntaxException
     * @throws InterruptedException
     */
    public void tryToConnectToService(Predicate<Connection> validationPredicate, boolean shouldSucceed) throws IOException {
        Connection connection;
        Registration serviceRegistration;
        Channel sendChannel = null, recvChannel = null;
        final FutureResult<Channel> passer = new FutureResult<Channel>();

        // register the service with the endpoint using the provided validationPredicate
        serviceRegistration = endpoint.registerService("org.jboss.test", new OpenListener() {
            public void channelOpened(final Channel channel) {
                passer.setResult(channel);
            }

            public void registrationTerminated() {
            }
        }, OptionMap.EMPTY, validationPredicate);

        // create a connection to the endpoint's connector
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

        // use the connection to open a channel to the service
        IoFuture.Status status = IoFuture.Status.WAITING;
        IoFuture<Channel> futureChannel = connection.openChannel("org.jboss.test", OptionMap.EMPTY);
        try {
            status = futureChannel.awaitInterruptibly(2L, TimeUnit.SECONDS);
        } catch(InterruptedException e) {
            throw new RuntimeException(e);
        }

        Logger.getLogger("TEST").infof("service returned status %s", status);
        if (status == IoFuture.Status.DONE ) {
            Logger.getLogger("TEST").infof("Channel creation succeeded");
            sendChannel = futureChannel.get();
            recvChannel = passer.getIoFuture().get();
            assertNotNull(recvChannel);
            assertNull("No SSLSession", recvChannel.getConnection().getSslSession());
            if (!shouldSucceed)
                fail("Channel creation succeeded when it should have failed!");
        } else if (status == IoFuture.Status.FAILED) {
            Exception exception = futureChannel.getException();
            Logger.getLogger("TEST").infof("Channel creation failed with exception %s", exception);
            if (shouldSucceed)
                fail("Channel creation failed when it should have succeeded!");
        }

        // service has been tested, we can shut down
        safeClose(sendChannel);
        safeClose(recvChannel);
        safeClose(connection);
        serviceRegistration.close();
    }

}
