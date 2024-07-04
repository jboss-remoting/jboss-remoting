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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ServerSocketChannel;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.EndpointBuilder;
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
import org.wildfly.security.auth.realm.SimpleRealmEntry;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.ssl.CipherSuiteSelector;
import org.wildfly.security.ssl.Protocol;
import org.wildfly.security.ssl.ProtocolSelector;
import org.wildfly.security.ssl.SSLContextBuilder;
import org.wildfly.security.ssl.test.util.CAGenerationTool;
import org.wildfly.security.ssl.test.util.CAGenerationTool.Identity;
import org.wildfly.security.ssl.test.util.DefinedCAIdentity;
import org.xnio.IoFuture;
import org.xnio.IoFuture.Status;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * Attempt to connect to a non-responding socket. Since the socket does not respond, the
 * IoFuture<Connection> returned by Endpoint.connect stays on WAITING forever.
 * In an attempt to solve the issue, this method tries to close the endpoint.
 *
 * <p>
 * This test is a reproduction of the scenario described by AS7-3537.
 *
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
public class TimeOutConnectionTestCase {

    private static final String CA_LOCATION = "./target/test-classes/ca";
    private static final String CIPHER_SUITE = "TLS_AES_128_GCM_SHA256";
    protected static Endpoint endpoint;

    @Rule
    public TestName name = new TestName();

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

    private void doTest(AuthenticationContext authenticationContext, boolean useTLS) throws Exception {
        try (final ServerSocketChannel channel = ServerSocketChannel.open()) {
            channel.configureBlocking(true);
            channel.socket().bind(new InetSocketAddress("localhost", 30123));
            Thread acceptThread = new Thread(new Accept(channel));
            acceptThread.start();
            // create endpoint, auth provider, etc, create server
            final EndpointBuilder endpointBuilder = Endpoint.builder();
            final XnioWorker.Builder workerBuilder = endpointBuilder.buildXnioWorker(Xnio.getInstance());
            workerBuilder.setCoreWorkerPoolSize(4).setMaxWorkerPoolSize(4).setWorkerIoThreads(4);
            endpointBuilder.setEndpointName("test");
            try (Endpoint ep = endpointBuilder.build()) {
                endpoint = ep;
                final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
                final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
                domainBuilder.addRealm("mainRealm", mainRealm);
                domainBuilder.setDefaultRealmName("mainRealm");
                final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
                mainRealm.setIdentityMap(Collections.singletonMap("bob", new SimpleRealmEntry(
                Collections.singletonList(
                    new PasswordCredential(passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())))))));


                // create connect and close endpoint threads
                IoFuture<Connection> futureConnection = authenticationContext.run(new PrivilegedAction<IoFuture<Connection>>() {
                    public IoFuture<Connection> run() {
                        try {
                            return ep.connect(new URI(String.format("%s://localhost:30123", useTLS ? "remote+tls" : "remote")), OptionMap.EMPTY);
                        } catch (URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                assertEquals(Status.WAITING, futureConnection.await(500, TimeUnit.MILLISECONDS));
                ep.close();
                assertEquals(Status.CANCELLED, futureConnection.getStatus());
                acceptThread.join();
            } finally {
                endpoint = null;
            }
        }
    }

    private static AuthenticationContext getAuthenticationContext(final SSLContext clientContext) {
        AuthenticationContext authenticationContext = AuthenticationContext.empty()
            .with(MatchRule.ALL, AuthenticationConfiguration.empty()
                .useName("bob")
                .usePassword("pass"));

        if (clientContext != null) {
            authenticationContext = authenticationContext.withSsl(MatchRule.ALL, () -> clientContext);
        }

        return authenticationContext;
    }

    @Test
    public void test() throws Exception {
        doTest(getAuthenticationContext(null), false);
    }

    @Test
    public void testSslEnabled() throws Exception {
        CAGenerationTool caGenerationTool = CAGenerationTool.builder()
                .setBaseDir(CA_LOCATION)
                .setRequestIdentities(Identity.LADYBIRD)
                .build();

                        DefinedCAIdentity ca = caGenerationTool.getDefinedCAIdentity(Identity.CA);
        SSLContext clientContext = new SSLContextBuilder()
                .setCipherSuiteSelector(CipherSuiteSelector.fromNamesString(CIPHER_SUITE))
                .setProtocolSelector(ProtocolSelector.empty().add(Protocol.TLSv1_3))
                .setTrustManager(ca.createTrustManager())
                .setClientMode(true)
                .build()
                .create();

        try {
            doTest(getAuthenticationContext(clientContext), true);
        } finally {
            caGenerationTool.close();
        }
    }

    private class Accept implements Runnable {
        private final ServerSocketChannel channel;

        public Accept(ServerSocketChannel channel) {
            this.channel = channel;
        }

        @Override
        public void run() {
            try {
                channel.accept();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
