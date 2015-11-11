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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivilegedAction;
import java.security.spec.InvalidKeySpecException;

import javax.security.sasl.SaslServerFactory;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
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
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * Test for remote channel communication with SSL enabled.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@Ignore // until SSL works in Elytron for both client and server
public final class RemoteSslChannelTest extends ChannelTestBase {
    protected static Endpoint endpoint;
    private static AcceptingChannel<? extends ConnectedStreamChannel> streamServer;
    private Connection connection;
    private Registration serviceRegistration;

    @BeforeClass
    public static void create() throws IOException, NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException {
        SslHelper.setKeyStoreAndTrustStore();
        endpoint = Endpoint.builder().setEndpointName("test").build();
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
        domainBuilder.addRealm("mainRealm", mainRealm);
        domainBuilder.setDefaultRealmName("mainRealm");
        final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
        mainRealm.setPasswordMap("bob", "clear-password", passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())));
        final SaslServerFactory saslServerFactory = new ServiceLoaderSaslServerFactory(RemoteSslChannelTest.class.getClassLoader());
        final SaslAuthenticationFactory.Builder builder = new SaslAuthenticationFactory.Builder();
        builder.setSecurityDomain(domainBuilder.build());
        builder.setSaslServerFactory(saslServerFactory);
        builder.addMechanism(SaslMechanismInformation.Names.SCRAM_SHA_256, MechanismConfiguration.EMPTY);
        final SaslAuthenticationFactory saslAuthenticationFactory = builder.build();
        streamServer = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123),
                OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE, Options.SASL_MECHANISMS, Sequence.of("CRAM-MD5")), saslAuthenticationFactory);
    }

    @Before
    public void testStart() throws IOException, URISyntaxException, InterruptedException {
        final FutureResult<Channel> passer = new FutureResult<Channel>();
        serviceRegistration = endpoint.registerService("org.jboss.test", new OpenListener() {
            public void channelOpened(final Channel channel) {
                passer.setResult(channel);
            }

            public void registrationTerminated() {
            }
        }, OptionMap.EMPTY);
        IoFuture<Connection> futureConnection = AuthenticationContext.empty().with(MatchRule.ALL, AuthenticationConfiguration.EMPTY.useName("bob").usePassword("pass")).run(new PrivilegedAction<IoFuture<Connection>>() {
            public IoFuture<Connection> run() {
                try {
                    return endpoint.connect(new URI("remote://localhost:30123"), OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE));
                } catch (IOException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        connection = futureConnection.get();
        assertNotNull("SSLSession Available", connection.getSslSession());
        IoFuture<Channel> futureChannel = connection.openChannel("org.jboss.test", OptionMap.EMPTY);
        sendChannel = futureChannel.get();
        recvChannel = passer.getIoFuture().get();
        assertNotNull(recvChannel);
        assertNotNull("SSLSession Available", recvChannel.getConnection().getSslSession());
    }

    @After
    public void testFinish() {
        IoUtils.safeClose(sendChannel);
        IoUtils.safeClose(recvChannel);
        IoUtils.safeClose(connection);
        serviceRegistration.close();
    }

    @AfterClass
    public static void destroy() throws IOException, InterruptedException {
        IoUtils.safeClose(streamServer);
        IoUtils.safeClose(endpoint);
    }
}
