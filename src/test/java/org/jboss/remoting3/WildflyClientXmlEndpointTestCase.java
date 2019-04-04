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

import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.client.config.ClientConfiguration;
import org.wildfly.client.config.ConfigXMLParseException;
import org.xnio.OptionMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.util.Map;

/**
 * Tests Endpoint parsing from RemotingXmlParser
 *
 * @author <a href="mailto:jbaesner@redhat.com">Joerg Baesner</a>
 */
public class WildflyClientXmlEndpointTestCase {

    private static final Logger logger = Logger.getLogger(WildflyClientXmlEndpointTestCase.class);
    private static final String CONFIGURATION_FILE_SYSTEM_PROPERTY_NAME = "wildfly.config.url";
    private static final String CONFIGURATION_FILE = "wildfly-config.xml";

    /**
     * Do any general setup here
     * 
     * @throws Exception
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        // make sure the desired configuration file is picked up
        ClassLoader cl = WildflyClientXmlEndpointTestCase.class.getClassLoader();
        URL resource = cl != null ? cl.getResource(CONFIGURATION_FILE) : ClassLoader.getSystemResource(CONFIGURATION_FILE);
        File file = new File(resource.getFile());
        System.setProperty(CONFIGURATION_FILE_SYSTEM_PROPERTY_NAME, file.getAbsolutePath());
    }

    @Test
    public void testParseEndpoint() {
        try {
            Endpoint endpoint = RemotingXmlParser.parseEndpoint();

            // make sure it's the correct Class...
            assertTrue(endpoint instanceof EndpointImpl);
            EndpointImpl endpointImpl = (EndpointImpl) endpoint;

            // get access to the 'connectionOptions' of the EndpointImpl
            Field f = endpointImpl.getClass().getDeclaredField("connectionOptions");
            f.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<URI, OptionMap> connectionOptions = (Map<URI, OptionMap>) f.get(endpointImpl);
            
            // we're expecting 2 URIs with the configuration in 'wildfly-config.xml'
            assertEquals(2, connectionOptions.size());
            
        } catch (Exception e) {
            String message = "Parsing the 'wildfly-config.xml' must not result in an Exception";
            logger.debug(message, e);
            fail(message);
        }
    }
}
