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
import org.jboss.remoting3.security.ServerAuthenticationProvider;
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

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthenticationException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;

@Test(suiteName = "Remote tests")
public final class RemoteTestCase extends InvocationTestBase {

    @BeforeTest
    public void setUp() throws IOException {
        enter();
        try {
            super.setUp();
            endpoint.addProtocolService(ProtocolServiceType.SERVER_AUTHENTICATION_PROVIDER, "test", new ServerAuthenticationProvider() {
                public CallbackHandler getCallbackHandler() {
                    return new CallbackHandler() {
                        public void handle(final Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                            for (Callback callback : callbacks) {
                                if (callback instanceof NameCallback) {
                                    final NameCallback nameCallback = (NameCallback) callback;
                                    final String defaultName = nameCallback.getDefaultName();
                                    if (defaultName != null) {
                                        nameCallback.setName(defaultName);
                                    }
                                    if (!"user".equals(nameCallback.getName())) {
                                        throw new AuthenticationException("Invalid user name");
                                    }
                                } else if (callback instanceof PasswordCallback) {
                                    final PasswordCallback passwordCallback = (PasswordCallback) callback;
                                    passwordCallback.setPassword("password".toCharArray());
                                } else if (callback instanceof RealmCallback) {
                                    // allow
                                } else if (callback instanceof AuthorizeCallback) {
                                    final AuthorizeCallback authorizeCallback = (AuthorizeCallback) callback;
                                    authorizeCallback.setAuthorized(authorizeCallback.getAuthenticationID().equals(authorizeCallback.getAuthorizationID()));
                                } else {
                                    throw new UnsupportedCallbackException(callback, "Callback not supported: " + callback);
                                }
                            }
                        }
                    };
                }
            });
        } finally {
            exit();
        }
    }

    protected Connection getConnection() throws IOException {
        final NetworkServerProvider provider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final ChannelListener<ConnectedStreamChannel<InetSocketAddress>> listener = provider.getServerListener(OptionMap.builder().set(RemotingOptions.AUTHENTICATION_PROVIDER, "test").setSequence(Options.SASL_MECHANISMS, "DIGEST-MD5").getMap());
        final Xnio xnio = Xnio.getInstance();
        try {
//            final AcceptingServer<InetSocketAddress, ?, ?> server = xnio.createSslTcpServer(listener, OptionMap.EMPTY);
            final AcceptingServer<InetSocketAddress, ?, ?> server = xnio.createTcpServer(listener, OptionMap.EMPTY);
            final IoFuture<? extends BoundChannel<InetSocketAddress>> future = server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            final InetSocketAddress localAddress = future.get().getLocalAddress();
            final Connection connection = endpoint.connect(new URI("remote", null, localAddress.getAddress().getHostAddress(), localAddress.getPort(), null, null, null), OptionMap.EMPTY, "user", null, "password".toCharArray()).get();
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