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

package org.jboss.remoting3;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.security.Security;
import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.remoting3.test.Utils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.wildfly.security.WildFlyElytronProvider;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

public class ConnectionBuilderTestCase {

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

    /**
     * Tests that setting some values works.
     * @throws Exception
     */
    @Test
    public void setValuesTest() throws Exception {
        // create endpoint
        final EndpointBuilder endpointBuilder = Endpoint.builder();
        URI uri = URI.create("remote+http://localhost:30123");
        ConnectionBuilder connectionBuilder = endpointBuilder.addConnection(uri);
        connectionBuilder.setHeartbeatInterval(500);
        connectionBuilder.setReadTimeout(0);
        connectionBuilder.setWriteTimeout(5000);
        connectionBuilder.setTcpKeepAlive(false);
        final XnioWorker.Builder workerBuilder = endpointBuilder.buildXnioWorker(Xnio.getInstance());
        workerBuilder.setCoreWorkerPoolSize(4).setMaxWorkerPoolSize(4).setWorkerIoThreads(4);
        endpointBuilder.setEndpointName("test");

        Endpoint ep = endpointBuilder.build();

        Map<URI, OptionMap> connectionOptions = (Map<URI, OptionMap>) Utils.getInstanceValue(ep, "connectionOptions");
        OptionMap optionMap = connectionOptions.get(uri);

        assertEquals("Wrong value for readtimeout", 0, optionMap.get(Options.READ_TIMEOUT, 5));
        assertEquals("Wrong value for writetimeout", 5000, optionMap.get(Options.WRITE_TIMEOUT, 5));
        assertEquals("Wrong value for heartbeat", 500, optionMap.get(RemotingOptions.HEARTBEAT_INTERVAL, 5));
        assertEquals("Wrong value for keep_alive", false, optionMap.get(Options.KEEP_ALIVE, true));
        ep.close();
        ep = null;
    }

    /**
     * Tests that uses the default values.
     * @throws Exception
     */
    @Test
    public void defaultValuesTest() throws Exception {
        // create endpoint
        final EndpointBuilder endpointBuilder = Endpoint.builder();
        URI uri = URI.create("remote+http://localhost:30123");
        endpointBuilder.addConnection(uri);
        final XnioWorker.Builder workerBuilder = endpointBuilder.buildXnioWorker(Xnio.getInstance());
        workerBuilder.setCoreWorkerPoolSize(4).setMaxWorkerPoolSize(4).setWorkerIoThreads(4);
        endpointBuilder.setEndpointName("test");

        Endpoint ep = endpointBuilder.build();

        Map<URI, OptionMap> connectionOptions = (Map<URI, OptionMap>) Utils.getInstanceValue(ep, "connectionOptions");
        OptionMap optionMap = connectionOptions.get(uri);

        assertEquals("Wrong value for readtimeout", RemotingOptions.DEFAULT_HEARTBEAT_INTERVAL * 2, optionMap.get(Options.READ_TIMEOUT, 5));
        assertEquals("Wrong value for writetimeout", RemotingOptions.DEFAULT_HEARTBEAT_INTERVAL * 2, optionMap.get(Options.WRITE_TIMEOUT, 5));
        assertEquals("Wrong value for heartbeat", RemotingOptions.DEFAULT_HEARTBEAT_INTERVAL, optionMap.get(RemotingOptions.HEARTBEAT_INTERVAL, 5));
        assertEquals("Wrong value for keep_alive", true, optionMap.get(Options.KEEP_ALIVE, false));
        ep.close();
        ep = null;
    }

}
