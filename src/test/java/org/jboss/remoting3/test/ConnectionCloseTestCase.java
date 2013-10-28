/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;

import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.security.SimpleServerAuthenticationProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.ConnectedStreamChannel;

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

    private static AcceptingChannel<? extends ConnectedStreamChannel> streamServer;
    private static Registration connectionProviderRegistration;
    private Connection connection;
    private Registration serviceRegistration;

    @BeforeClass
    public static void beforeClass() throws IOException {
        endpoint = Remoting.createEndpoint("test", OptionMap.EMPTY);
        connectionProviderRegistration = endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        SimpleServerAuthenticationProvider provider = new SimpleServerAuthenticationProvider();
        provider.addUser("bob", "test", "pass".toCharArray());
        streamServer = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123), OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("CRAM-MD5")), provider, null);
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
        IoFuture<Connection> futureConnection = endpoint.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY, "bob", "test", "pass".toCharArray());
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
        IoUtils.safeClose(connectionProviderRegistration);
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
