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
package org.jboss.remoting3.test.racecondition;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;

import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.security.SimpleServerAuthenticationProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.jboss.remoting3.test.RemoteSslChannelTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xnio.IoFuture;
import org.xnio.IoFuture.Status;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;

/**
 * Tests the cancellation of an ssl opening connection.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(BMUnitRunner.class)
@BMScript(value = "AbstractCancelOpeningConnectionTest.btm", dir="src/test/resources/org/jboss/remoting3/test/racecondition")
public class CancelOpeningSslConnectionTestCase extends AbstractCancelOpeningConnectionTest {
    private static final String KEY_STORE_PROPERTY = "javax.net.ssl.keyStore";
    private static final String KEY_STORE_PASSWORD_PROPERTY = "javax.net.ssl.keyStorePassword";
    private static final String TRUST_STORE_PROPERTY = "javax.net.ssl.trustStore";
    private static final String TRUST_STORE_PASSWORD_PROPERTY = "javax.net.ssl.trustStorePassword";
    private static final String DEFAULT_KEY_STORE = "keystore.jks";
    private static final String DEFAULT_KEY_STORE_PASSWORD = "jboss-remoting-test";

    private static void setKeyStoreAndTrustStore() {
        final URL storePath = RemoteSslChannelTest.class.getClassLoader().getResource(DEFAULT_KEY_STORE);
        if (System.getProperty(KEY_STORE_PROPERTY) == null) {
            System.setProperty(KEY_STORE_PROPERTY, storePath.getFile());
        }
        if (System.getProperty(KEY_STORE_PASSWORD_PROPERTY) == null) {
            System.setProperty(KEY_STORE_PASSWORD_PROPERTY, DEFAULT_KEY_STORE_PASSWORD);
        }
        if (System.getProperty(TRUST_STORE_PROPERTY) == null) {
            System.setProperty(TRUST_STORE_PROPERTY, storePath.getFile());
        }
        if (System.getProperty(TRUST_STORE_PASSWORD_PROPERTY) == null) {
            System.setProperty(TRUST_STORE_PASSWORD_PROPERTY, DEFAULT_KEY_STORE_PASSWORD);
        }
    }

    private boolean startTLS = false;

    @Override
    protected IoFuture<Connection> connect() throws Exception {
        setKeyStoreAndTrustStore();
        final OptionMap connProviderOptionMap = startTLS? OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE, Options.SSL_STARTTLS, Boolean.TRUE):
            OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE);
        final OptionMap serverOptionMap=  OptionMap.builder().addAll(connProviderOptionMap).set(Options.SASL_MECHANISMS, Sequence.of("CRAM-MD5")).getMap();
        // create endpoint, auth provider, etc, create server
        endpoint = Remoting.createEndpoint("test", OptionMap.EMPTY);
        endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), connProviderOptionMap);
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        SimpleServerAuthenticationProvider provider = new SimpleServerAuthenticationProvider();
        provider.addUser("bob", "test", "pass".toCharArray());
        serverAcceptingChannel = networkServerProvider.createServer(new InetSocketAddress("::1", 30123),
                serverOptionMap, provider, null);
        // connect
        return endpoint.connect(new URI("remote://[::1]:30123"), OptionMap.create(Options.SSL_STARTTLS, startTLS), "bob", "test", "pass".toCharArray());
    }

    @Test
    @BMRule(name="after ClientConnectionOpenListener$Capabilities receives response", targetClass="org.jboss.remoting3.remote.ClientConnectionOpenListener$Capabilities",
            targetMethod="handleEvent", condition="TRUE", targetLocation="AFTER INVOKE org.jboss.remoting3.remote.RemoteConnection.setReadListener",
            action="debug(\"Client received Capabilities response, waiting for cancel\"),\n" +
    "signalWake(\"cancel\", true),\n" +
    "waitFor(\"canceled\"),\n" + 
    "debug(\"proceeding\")")
    public void cancelAfterClientReceivedCapabilities() throws Exception {
        startTLS = true;
        try {
            cancelFutureConnection();
        } finally {
            startTLS = false;
        }
        assertSame(Status.DONE, OpeningConnectionTestHelper.getConnectedStreamChannelFuture().getStatus());
        assertSame(Status.CANCELLED, OpeningConnectionTestHelper.getConnectionHandlerFactoryFuture().getStatus());
        OpeningConnectionTestHelper.waitWorkerThreadFinished();
        assertTrue(OpeningConnectionTestHelper.isClientConnectionOpenListenerCreated());
    }

}
