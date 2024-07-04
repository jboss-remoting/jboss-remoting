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

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.wildfly.security.sasl.util.SaslMechanismInformation;
import org.wildfly.security.ssl.CipherSuiteSelector;
import org.wildfly.security.ssl.Protocol;
import org.wildfly.security.ssl.ProtocolSelector;
import org.wildfly.security.ssl.SSLContextBuilder;
import org.wildfly.security.ssl.test.util.CAGenerationTool;
import org.wildfly.security.ssl.test.util.CAGenerationTool.Identity;
import org.wildfly.security.ssl.test.util.DefinedCAIdentity;
import org.wildfly.security.ssl.test.util.DefinedIdentity;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * Test for remote channel communication with SSL enabled.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class RemoteSslChannelTest extends ChannelTestBase {

    private static final String CA_LOCATION = "./target/test-classes/ca";
    private static final String CIPHER_SUITE = "TLS_AES_128_GCM_SHA256";
    private static final String PROTOCOL = "remote+tls";
    private static final OptionMap optionMap = OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE);
    private static final String SASL_MECH = SaslMechanismInformation.Names.SCRAM_SHA_256_PLUS;

    private static CAGenerationTool caGenerationTool = null;

    @BeforeClass
    public static void create() throws Exception {
        // SSL Configuration
        caGenerationTool = CAGenerationTool.builder()
                .setBaseDir(CA_LOCATION)
                .setRequestIdentities(Identity.LADYBIRD)
                .build();

        DefinedIdentity ladybird = caGenerationTool.getDefinedIdentity(Identity.LADYBIRD);

        SSLContext serverContext = new SSLContextBuilder()
                .setCipherSuiteSelector(CipherSuiteSelector.fromNamesString(CIPHER_SUITE))
                .setProtocolSelector(ProtocolSelector.empty().add(Protocol.TLSv1_3))
                .setKeyManager(ladybird.createKeyManager())
                .build()
                .create();

        ChannelTestBase.create(optionMap, SASL_MECH, serverContext);
    }

    @Before
    public void testStart() throws IOException, URISyntaxException, InterruptedException, GeneralSecurityException {
        DefinedCAIdentity ca = caGenerationTool.getDefinedCAIdentity(Identity.CA);
        SSLContext clientContext = new SSLContextBuilder()
                .setCipherSuiteSelector(CipherSuiteSelector.fromNamesString(CIPHER_SUITE))
                .setProtocolSelector(ProtocolSelector.empty().add(Protocol.TLSv1_3))
                .setTrustManager(ca.createTrustManager())
                .setClientMode(true)
                .build()
                .create();

        testStart(SASL_MECH, clientContext, PROTOCOL, optionMap);
    }

    @AfterClass
    public static void destroy() throws IOException, InterruptedException {
        caGenerationTool.close();
    }
}
