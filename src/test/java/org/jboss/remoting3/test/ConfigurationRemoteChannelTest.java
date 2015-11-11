/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.xnio.IoUtils.safeClose;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.security.sasl.SaslServerFactory;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.client.config.ConfigXMLParseException;
import org.wildfly.common.selector.Selector;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.ElytronXmlParser;
import org.wildfly.security.auth.provider.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.sasl.util.SaslMechanismInformation;
import org.wildfly.security.sasl.util.ServiceLoaderSaslServerFactory;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * Test for remote channel communication.
 *
 * @author Kabir Khan
 */
public final class ConfigurationRemoteChannelTest extends ChannelTestBase {
    //From the remoting section of wildfly-config.xml
    static final Selector.Getter<Endpoint> ENDPOINT_GETTER = AccessController.doPrivileged(Selector.selectorGetterActionFor(Endpoint.class));

    protected static Endpoint endpoint;
    private static AcceptingChannel<? extends ConnectedStreamChannel> streamServer;
    private Connection connection;
    private Registration serviceRegistration;

    @BeforeClass
    public static void create() throws Exception {
        endpoint = ENDPOINT_GETTER.getSelector().get();
        //From the xml
        Assert.assertEquals("config-endpoint", endpoint.getName());

        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
        domainBuilder.addRealm("mainRealm", mainRealm);
        domainBuilder.setDefaultRealmName("mainRealm");
        final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
        mainRealm.setPasswordMap("bob", "clear-password", passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())));
        final SaslServerFactory saslServerFactory = new ServiceLoaderSaslServerFactory(RemoteChannelTest.class.getClassLoader());
        final SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder();
        builder.setSecurityDomain(domainBuilder.build());
        builder.setSaslServerFactory(saslServerFactory);
        builder.addMechanism(SaslMechanismInformation.Names.SCRAM_SHA_256, MechanismConfiguration.EMPTY);
        final SaslAuthenticationFactory saslAuthenticationFactory = builder.build();
        streamServer = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123), OptionMap.EMPTY, saslAuthenticationFactory);
    }

    @Before
    public void testStart() throws IOException, URISyntaxException, InterruptedException, ConfigXMLParseException, GeneralSecurityException {
        final FutureResult<Channel> passer = new FutureResult<Channel>();
        serviceRegistration = endpoint.registerService("org.jboss.test", new OpenListener() {
            public void channelOpened(final Channel channel) {
                passer.setResult(channel);
            }

            public void registrationTerminated() {
            }
        }, OptionMap.EMPTY);
        AuthenticationContext configContext = ElytronXmlParser.parseAuthenticationClientConfiguration().create();

        IoFuture<Connection> futureConnection = configContext.run(new PrivilegedAction<IoFuture<Connection>>() {
            public IoFuture<Connection> run() {
                try {
                    return endpoint.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY);
                } catch (IOException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        connection = futureConnection.get();
        assertNull("No SSLSession", connection.getSslSession());
        IoFuture<Channel> futureChannel = connection.openChannel("org.jboss.test", OptionMap.EMPTY);
        sendChannel = futureChannel.get();
        recvChannel = passer.getIoFuture().get();
        assertNotNull(recvChannel);
        assertNull("No SSLSession", recvChannel.getConnection().getSslSession());
        //assertEquals("bob",recvChannel.getConnection().getUserInfo().getUserName());
    }

    @After
    public void testFinish() {
        safeClose(sendChannel);
        safeClose(recvChannel);
        safeClose(connection);
        serviceRegistration.close();
    }

    @AfterClass
    public static void destroy() throws IOException, InterruptedException {
        safeClose(streamServer);
        safeClose(endpoint);
    }

    private void createKeyStore() {
        //Create the keystore in the
    }

    @Test
    public void testRefused() throws Exception {
        IoFuture<Connection> futureConnection = endpoint.connect(new URI("remote://localhost:33123"), OptionMap.EMPTY);
        try {
            futureConnection.awaitInterruptibly(2L, TimeUnit.SECONDS);
            if (futureConnection.getStatus() == IoFuture.Status.WAITING) {
                futureConnection.cancel();
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
        fail("Expected an IOException with 'refused' in the string");
    }
}
