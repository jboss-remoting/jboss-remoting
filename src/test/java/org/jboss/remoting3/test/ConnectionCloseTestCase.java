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

import static org.junit.Assert.assertNotNull;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.security.Security;

import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslServerFactory;

import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.auth.realm.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.permission.PermissionVerifier;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.wildfly.security.sasl.util.SaslMechanismInformation;
import org.wildfly.security.sasl.util.ServiceLoaderSaslServerFactory;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;

/**
 * A testcase to ensure that threads don't hang when the client side closes a {@link Connection} while the
 * server side is still sending messages on a channel created over that connection
 *
 * @author Jaikiran Pai
 */
public class ConnectionCloseTestCase {

    private static final Logger logger = Logger.getLogger(ConnectionCloseTestCase.class);

    private static Endpoint endpoint;
    private Channel clientChannel;
    private Channel serverChannel;

    private static Closeable streamServer;
    private Connection connection;
    private Registration serviceRegistration;
    private static String providerName;

    @BeforeClass
    public static void beforeClass() throws Exception {
        final WildFlyElytronProvider provider = new WildFlyElytronProvider();
        Security.addProvider(provider);
        providerName = provider.getName();
        endpoint = Endpoint.builder().setEndpointName("test").build();
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
        domainBuilder.addRealm("mainRealm", mainRealm).build();
        domainBuilder.setDefaultRealmName("mainRealm");
        domainBuilder.setPermissionMapper((permissionMappable, roles) -> PermissionVerifier.ALL);
        final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
        mainRealm.setPasswordMap("bob", passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())));
        final SaslServerFactory saslServerFactory = new ServiceLoaderSaslServerFactory(ConnectionCloseTestCase.class.getClassLoader());

        final SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder();
        builder.setSecurityDomain(domainBuilder.build());
        builder.setFactory(saslServerFactory);
        builder.setMechanismConfigurationSelector(mechanismInformation -> SaslMechanismInformation.Names.SCRAM_SHA_256.equals(mechanismInformation.getMechanismName()) ? MechanismConfiguration.EMPTY : null);
        final SaslAuthenticationFactory saslAuthenticationFactory = builder.build();
        streamServer = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123), OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("CRAM-MD5"), Options.SSL_ENABLED, Boolean.FALSE), saslAuthenticationFactory, SSLContext.getDefault());
    }

    @AfterClass
    public static void doAfterClass() {
        Security.removeProvider(providerName);
    }

    @Before
    public void before() throws IOException, URISyntaxException, InterruptedException {
        final FutureResult<Channel> passer = new FutureResult<Channel>();
        serviceRegistration = endpoint.registerService("org.jboss.test", new OpenListener() {
            public void channelOpened(final Channel channel) {
                passer.setResult(channel);
            }

            public void registrationTerminated() {
            }
        }, OptionMap.EMPTY);
        IoFuture<Connection> futureConnection = AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.empty().useName("bob").usePassword("pass").setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism("SCRAM-SHA-256"))).run(new PrivilegedAction<IoFuture<Connection>>() {
            public IoFuture<Connection> run() {
                try {
                    return endpoint.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        connection = futureConnection.get();
        IoFuture<Channel> futureChannel = connection.openChannel("org.jboss.test", OptionMap.EMPTY);
        clientChannel = futureChannel.get();
        serverChannel = passer.getIoFuture().get();
        assertNotNull(serverChannel);

    }

    @After
    public void after() {
        IoUtils.safeClose(clientChannel);
        IoUtils.safeClose(serverChannel);
        IoUtils.safeClose(connection);
        serviceRegistration.close();
    }

    @AfterClass
    public static void afterClass() throws IOException, InterruptedException {
        IoUtils.safeClose(streamServer);
        IoUtils.safeClose(endpoint);
    }

    /**
     * Tests that when the client closes a connection when the server is still sending messages on a
     * channel, over that connection, the server and client don't end up hanging
     *
     * @throws Exception
     */
    @Test
    public void testConnectionCloseWhenServerIsSendingMessage() throws Exception {
        clientChannel.receiveMessage(new ConnectionClosingChannelReceiver());
        final DataOutputStream outputStream = new DataOutputStream(serverChannel.writeMessage());
        logger.info("Server will start sending a continuous stream of messages");
        // doesn't matter if it's a long string or even a simple byte message, but we are just
        // trying to increase the probability of race condition (which is what it looks like)
        // in remoting
        final StringBuffer longString = new StringBuffer();
        for (int i = 0; i < 300; i++) {
            longString.append("The quick brown fox jumps over the lazy dog");
        }
        while (true) {
            try {
                outputStream.writeUTF(longString.toString());
            } catch (Exception e) {
                // the client closes the connection so we will/should get an exception
                logger.info("Got an (expected) exception", e);
                break;
            }
        }

    }

    /**
     * A channel receiver which just closes the {@link Connection} on receiving a message
     */
    private class ConnectionClosingChannelReceiver implements Channel.Receiver {

        @Override
        public void handleError(Channel channel, IOException error) {
            logger.error("Error on channel " + channel, error);
        }

        @Override
        public void handleEnd(Channel channel) {
            logger.info("Channel end notification received for channel " + channel);
        }

        @Override
        public void handleMessage(Channel channel, MessageInputStream message) {
            // first close the message
            try {
                logger.info("Client closing message");
                message.close();
            } catch (IOException e) {
                // ignore
                logger.info("Error while closing message on client side", e);
            }
            logger.info("Client closed message");
            // now close the connection
            try {
                logger.info("Client closing connection " + channel.getConnection());
                channel.getConnection().close();
            } catch (IOException e) {
                logger.error("Could not close connection", e);
            }
            logger.info("Client closed connection");
        }
    }

}
