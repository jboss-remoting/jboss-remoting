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

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CancellationException;

import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.security.SimpleServerAuthenticationProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;

/**
 * At EndpointImpl: closeAction takes place while there is a new connection being made.
 * This test makes sure resourceCount does not get inconsistent (there is a window between a connection being added to
 * resourceCount and being added to connections map, this is the window that will be explored by this test).
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@RunWith(BMUnitRunner.class)
@BMScript(dir="src/test/resources")
public class CloseConnectingEndpointTestCase {

    protected static Endpoint endpoint;

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

    @Test
    public void test() throws Exception {
        // create endpoint, auth provider, etc, create server
        endpoint = Remoting.createEndpoint("test", OptionMap.EMPTY);
        endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        SimpleServerAuthenticationProvider provider = new SimpleServerAuthenticationProvider();
        provider.addUser("bob", "test", "pass".toCharArray());
        networkServerProvider.createServer(new InetSocketAddress("localhost", 30123), OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("CRAM-MD5")), provider, null);
        // create connect and close endpoint threads
        Connect connectRunnable = new Connect(endpoint);
        Thread connectThread = new Thread(connectRunnable);
        Thread closeEndpointThread = new Thread(new CloseEndpoint(endpoint));
        // execute and run threads
        connectThread.start();
        closeEndpointThread.start();
        connectThread.join();
        closeEndpointThread.join();
        // connect runnable is supposed to have failed if race condition was safely reproduced
        //(see CloseConnectingEndpointTestCase.btm for further info)
        assertTrue(connectRunnable.hasFailed());
    }

    private class Connect implements Runnable {
        private boolean failed = false;
        private final Endpoint endpoint;

        public Connect(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void run() {
            final IoFuture<Connection> futureConnection;
            try {
                futureConnection = endpoint.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY, "bob", "test", "pass".toCharArray());
                if (futureConnection != null) {
                    Connection c = futureConnection.get();
                    if (c != null) {
                        c.close();
                    }
                }
            } catch (CancellationException e) {
                // exception expected, because server will send an AUTH_REJECTED as soon as it realizes the connection is closed
                failed = true;
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean hasFailed() {
            return failed;
        }
    }

    private static class CloseEndpoint implements Runnable {
        private final Endpoint endpoint;

        public CloseEndpoint(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void run() {
            try {
                endpoint.close();
                endpoint.awaitClosed();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
