/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.security.SimpleServerAuthenticationProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.jboss.remoting3.spi.ProtocolServiceType;
import org.jboss.xnio.AcceptingServer;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Options;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.channels.BoundChannel;
import org.jboss.xnio.channels.ConnectedStreamChannel;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@Test(suiteName = "Remote tests")
public final class RemoteTestCase extends InvocationTestBase {

    @BeforeTest
    public void setUp() throws IOException {
        enter();
        try {
            super.setUp();
            final SimpleServerAuthenticationProvider authenticationProvider = new SimpleServerAuthenticationProvider();
            authenticationProvider.addUser("user", "endpoint", "password".toCharArray());
            endpoint.addProtocolService(ProtocolServiceType.SERVER_AUTHENTICATION_PROVIDER, "test", authenticationProvider);
        } finally {
            exit();
        }
    }

    protected Connection getConnection() throws IOException {
        final NetworkServerProvider provider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final ChannelListener<ConnectedStreamChannel<InetSocketAddress>> listener = provider.getServerListener(OptionMap.builder().set(RemotingOptions.AUTHENTICATION_PROVIDER, "test").setSequence(Options.SASL_MECHANISMS, "DIGEST-MD5").getMap());
        final Xnio xnio = Xnio.getInstance();
        try {
//            final AcceptingServer<InetSocketAddress, ?, ?> server = xnio.createSslTcpServer(listener, OptionMap.builder().setSequence(Options.SSL_ENABLED_CIPHER_SUITES, "TLS_RSA_WITH_AES_128_CBC_SHA").getMap());
            final AcceptingServer<InetSocketAddress, ?, ?> server = xnio.createTcpServer(listener, OptionMap.EMPTY);
            final IoFuture<? extends BoundChannel<InetSocketAddress>> future = server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            final InetSocketAddress localAddress = future.get().getLocalAddress();
            final Connection connection = endpoint.connect(new URI("remote", null, localAddress.getAddress().getHostAddress(), localAddress.getPort(), null, null, null), OptionMap.builder().setSequence(Options.SSL_ENABLED_CIPHER_SUITES, "TLS_RSA_WITH_AES_128_CBC_SHA").getMap(), "user", null, "password".toCharArray()).get();
            connection.addCloseHandler(new CloseHandler<Connection>() {
                public void handleClose(final Connection closed) {
                    IoUtils.safeClose(server);
                }
            });
            return connection;
        } catch (Exception e) {
            final IOException ioe = new IOException();
            ioe.initCause(e);
            throw ioe;
        }
    }
}