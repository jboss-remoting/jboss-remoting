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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.security.SimpleServerAuthenticationProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
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
 * Tests that a {@link org.jboss.remoting3.MessageOutputStream#close() closing the message on the channel}
 * synchronously decrements the outbound message count maintained by JBoss Remoting.
 *
 * @author Jaikiran Pai
 * @see {@link}https://issues.jboss.org/browse/REM3-165 for more
 *      <p/>
 */
public class OutboundMessageCountTestCase {

    private static final Logger logger = Logger.getLogger(OutboundMessageCountTestCase.class);
    private static final int MAX_OUTBOUND_MESSAGES = 20;
    private static Endpoint endpoint;
    private Channel clientChannel;
    private Channel serverChannel;

    private static AcceptingChannel<? extends ConnectedStreamChannel> streamServer;
    private static Registration registration;
    private Connection connection;
    private Registration serviceRegistration;

    @BeforeClass
    public static void create() throws IOException {
        endpoint = Remoting.createEndpoint("test", OptionMap.EMPTY);
        registration = endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        SimpleServerAuthenticationProvider provider = new SimpleServerAuthenticationProvider();
        provider.addUser("bob", "test", "pass".toCharArray());
        streamServer = networkServerProvider.createServer(new InetSocketAddress("::1", 30123), OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("CRAM-MD5")), provider, null);
    }

    @Before
    public void beforeTest() throws IOException, URISyntaxException, InterruptedException {
        final FutureResult<Channel> passer = new FutureResult<Channel>();
        serviceRegistration = endpoint.registerService("org.jboss.test", new OpenListener() {
            public void channelOpened(final Channel channel) {
                passer.setResult(channel);
            }

            public void registrationTerminated() {
            }
        }, OptionMap.EMPTY);
        IoFuture<Connection> futureConnection = endpoint.connect(new URI("remote://[::1]:30123"), OptionMap.EMPTY, "bob", "test", "pass".toCharArray());
        connection = futureConnection.get();
        final OptionMap channelCreationOptions = OptionMap.create(RemotingOptions.MAX_OUTBOUND_MESSAGES, MAX_OUTBOUND_MESSAGES);
        IoFuture<Channel> futureChannel = connection.openChannel("org.jboss.test", channelCreationOptions);
        clientChannel = futureChannel.get();
        serverChannel = passer.getIoFuture().get();
        assertNotNull(serverChannel);

    }

    @After
    public void afterTest() {
        IoUtils.safeClose(connection);
        serviceRegistration.close();
    }

    @AfterClass
    public static void destroy() throws IOException, InterruptedException {
        IoUtils.safeClose(streamServer);
        IoUtils.safeClose(endpoint);
        IoUtils.safeClose(registration);
    }

    /**
     * Tests that multiple threads opening and closing a message on the channel doesn't cause the JBoss Remoting
     * code to get out of sync with the current outbound message count it maintains.
     *
     * @throws Exception
     */
    @Test
    public void testOutboundMessageSend() throws Exception {
        final int NUM_THREADS = 150;
        final ExecutorService executorService = Executors.newFixedThreadPool(NUM_THREADS);
        final Future<Throwable>[] futureFailures = new Future[NUM_THREADS];
        final Semaphore semaphore = new Semaphore(MAX_OUTBOUND_MESSAGES, true);
        try {
            // create and submit the tasks which will send out the messages
            for (int i = 0; i < NUM_THREADS; i++) {
                futureFailures[i] = executorService.submit(new MessageSender(this.clientChannel, semaphore));
            }
            int failureCount = 0;
            // wait for the tasks to complete and then collect any failures
            for (int i = 0; i < NUM_THREADS; i++) {
                final Throwable failure = futureFailures[i].get();
                if (failure == null) {
                    continue;
                }
                failureCount++;
                logger.info("Thread#" + i + " failed with exception", failure);
            }
            Assert.assertEquals("Some threads failed to send message on the channel", 0, failureCount);
        } finally {
            executorService.shutdown();
        }
    }

    private class MessageSender implements Callable<Throwable> {

        private Semaphore semaphore;
        private Channel channel;

        MessageSender(final Channel channel, final Semaphore semaphore) {
            this.semaphore = semaphore;
            this.channel = channel;
        }

        @Override
        public Throwable call() throws Exception {
            for (int i = 0; i < 3; i++) {
                try {
                    // get a permit before trying to send a message to the channel
                    this.semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return e;
                }
                MessageOutputStream messageOutputStream = null;
                try {
                    // now send a message
                    messageOutputStream = this.channel.writeMessage();
                    messageOutputStream.write("hello".getBytes());
                } catch (IOException e) {
                    return e;
                } finally {
                    // close the message
                    if (messageOutputStream != null) {
                        messageOutputStream.close();
                    }
                    // release the permit for others to use
                    this.semaphore.release();
                }
            }
            // no failures, return null
            return null;
        }
    }
}
