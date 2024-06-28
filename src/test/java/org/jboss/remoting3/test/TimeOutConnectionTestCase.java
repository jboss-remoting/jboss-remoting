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
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.EndpointBuilder;
import org.junit.After;
import org.junit.AfterClass;
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
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.xnio.IoFuture;
import org.xnio.IoFuture.Status;
import org.xnio.OptionMap;
import org.xnio.Options;
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

    private void doTest(OptionMap connectionProviderOptions) throws Exception {
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
                mainRealm.setPasswordMap("bob", passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())));
                // create connect and close endpoint threads
                IoFuture<Connection> futureConnection = AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.empty().useName("bob").usePassword("pass")).run(new PrivilegedAction<IoFuture<Connection>>() {
                    public IoFuture<Connection> run() {
                        try {
                            return ep.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY);
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

    @Test
    public void test() throws Exception {
        doTest(OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
    }

    @Test
    @Ignore
    public void testSslEnabled() throws Exception {
        //SslHelper.setKeyStoreAndTrustStore();
        doTest(OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE));
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
