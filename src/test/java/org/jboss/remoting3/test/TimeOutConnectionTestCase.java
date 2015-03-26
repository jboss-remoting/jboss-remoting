/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.AuthenticationConfiguration;
import org.wildfly.security.auth.AuthenticationContext;
import org.wildfly.security.auth.MatchRule;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.provider.SecurityDomain;
import org.wildfly.security.auth.provider.SimpleMapBackedSecurityRealm;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.xnio.IoFuture;
import org.xnio.IoFuture.Status;
import org.xnio.OptionMap;
import org.xnio.Options;

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
            try (Endpoint ep = Remoting.createEndpoint("test", OptionMap.EMPTY)) {
                endpoint = ep;
                ep.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), connectionProviderOptions);
                final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
                final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
                domainBuilder.addRealm("mainRealm", mainRealm);
                domainBuilder.setDefaultRealmName("mainRealm");
                final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
                mainRealm.setPasswordMap(Collections.singletonMap(new NamePrincipal("bob"), passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray()))));
                // create connect and close endpoint threads
                IoFuture<Connection> futureConnection = AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.EMPTY.useName("bob").usePassword("pass")).run(new PrivilegedAction<IoFuture<Connection>>() {
                    public IoFuture<Connection> run() {
                        try {
                            return ep.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY);
                        } catch (IOException | URISyntaxException e) {
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
    public void testSslEnabled() throws Exception {
        SslHelper.setKeyStoreAndTrustStore();
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
