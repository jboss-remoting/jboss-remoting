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

import static org.junit.Assert.fail;
import static org.xnio.IoUtils.safeClose;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.jboss.remoting3.Connection;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.wildfly.security.sasl.util.SaslMechanismInformation;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * Test for remote channel communication.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemoteChannelTest extends ChannelTestBase {

    private static final OptionMap optionMap = OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE);
    private static final String SASL_MECH = SaslMechanismInformation.Names.SCRAM_SHA_256;

    @BeforeClass
    public static void create() throws Exception {
        ChannelTestBase.create(optionMap, SASL_MECH, SSLContext.getDefault());
    }

    @Before
    public void testStart() throws Exception {
        testStart(SASL_MECH, null, optionMap);
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
