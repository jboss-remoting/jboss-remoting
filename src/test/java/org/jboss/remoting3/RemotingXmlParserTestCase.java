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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.Security;
import java.util.Map;

import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.wildfly.client.config.ConfigXMLParseException;
import org.wildfly.security.WildFlyElytronProvider;
import org.xnio.OptionMap;
import org.xnio.Options;

public class RemotingXmlParserTestCase {

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
        clearResources();
        System.gc();
        System.runFinalization();
        Logger.getLogger("TEST").infof("Finished test %s", name.getMethodName());
    }

    /**
     * Tests that setting some values works.
     * @throws Exception
     */
    @Test
    public void getEndpointAttributesTest() throws Exception {

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("wildfly-config-endpoint.xml").getFile());
        System.setProperty("wildfly.config.url", file.getAbsolutePath());
        // create endpoint
        Endpoint endpoint = RemotingXmlParser.parseEndpoint();
        OptionMap optionMap = ((EndpointImpl)endpoint).getDefaultConnectionOptionMap();

        assertEquals("Wrong value for readtimeout", 1000, optionMap.get(Options.READ_TIMEOUT, 0));
        assertEquals("Wrong value for writetimeout", 1000, optionMap.get(Options.WRITE_TIMEOUT, 0));
        assertEquals("Wrong value for heartbeat", 500, optionMap.get(RemotingOptions.HEARTBEAT_INTERVAL, 0));
        assertEquals("Wrong value for keep_alive", true, optionMap.get(Options.KEEP_ALIVE, false));
    }

    /**
     * Tests that uses the default values.
     * @throws Exception
     */
    @Test
    public void defaultValuesTest() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("wildfly-config-connection.xml").getFile());
        System.setProperty("wildfly.config.url", file.getAbsolutePath());
        // create endpoint
        Endpoint endpoint = RemotingXmlParser.parseEndpoint();
        OptionMap optionMap = ((EndpointImpl)endpoint).getDefaultConnectionOptionMap();

        assertEquals("Wrong value for readtimeout", 120000, optionMap.get(Options.READ_TIMEOUT, 0));
        assertEquals("Wrong value for writetimeout", 120000, optionMap.get(Options.WRITE_TIMEOUT, 0));
        assertEquals("Wrong value for heartbeat", 60000, optionMap.get(RemotingOptions.HEARTBEAT_INTERVAL, 0));
        assertEquals("Wrong value for keep_alive", true, optionMap.get(Options.KEEP_ALIVE, false));

        Map<URI, OptionMap> connectionOptions = ((EndpointImpl)endpoint).getConnectionOptions();

        OptionMap connectionOptionMap = connectionOptions.values().iterator().next();
        //check if values are merged
        assertEquals("Wrong value for readtimeout", 11000, connectionOptionMap.get(Options.READ_TIMEOUT, 0));
        assertEquals("Wrong value for writetimeout", 11000, connectionOptionMap.get(Options.WRITE_TIMEOUT, 0));
        assertEquals("Wrong value for heartbeat", 60000, connectionOptionMap.get(RemotingOptions.HEARTBEAT_INTERVAL, 0));
        assertEquals("Wrong value for keep_alive", true, connectionOptionMap.get(Options.KEEP_ALIVE, false));

    }

    /**
     * Tests that setting some values works from 51 does not work.
     * @throws Exception
     */
    @Test
    public void parse50ErrorTest() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("wildfly-config-50.xml").getFile());
        System.setProperty("wildfly.config.url", file.getAbsolutePath());
        boolean isConfigurationException = false;
        // create endpoint
        try {
            endpoint = RemotingXmlParser.parseEndpoint();
        } catch (Exception e) {
            if(e instanceof ConfigXMLParseException) {
                isConfigurationException = true;
            }
        }
        assertTrue("No configuration exception", isConfigurationException );
    }

    /**
     * Tests that parsing 50 connections works.
     * @throws Exception
     */
    @Test
    public void parse50Test() throws Exception {

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("wildfly-config-connection-50.xml").getFile());
        System.setProperty("wildfly.config.url", file.getAbsolutePath());
        // create endpoint
        Endpoint endpoint = RemotingXmlParser.parseEndpoint();
        Map<URI, OptionMap> connectionOptions = ((EndpointImpl)endpoint).getConnectionOptions();

        OptionMap connectionOptionMap = connectionOptions.values().iterator().next();
        //check if values are merged
        assertEquals("Wrong value for readtimeout", 11000, connectionOptionMap.get(Options.READ_TIMEOUT, 0));
        assertEquals("Wrong value for writetimeout", 11000, connectionOptionMap.get(Options.WRITE_TIMEOUT, 0));
        assertEquals("Wrong value for heartbeat", 60000, connectionOptionMap.get(RemotingOptions.HEARTBEAT_INTERVAL, 0));
        assertEquals("Wrong value for keep_alive", true, connectionOptionMap.get(Options.KEEP_ALIVE, false));

    }

    private void clearResources() {
        if (endpoint != null) {
            try {
                endpoint.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            endpoint = null;
        }
        System.clearProperty("wildfly.config.url");
    }

}
