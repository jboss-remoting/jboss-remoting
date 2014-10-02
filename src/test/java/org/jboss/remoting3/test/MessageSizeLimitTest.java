package org.jboss.remoting3.test;

import junit.framework.Assert;
import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageInputStream;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

/**
 * Test for REM3-170
 * @author <a href="mailto:jkudrnacd@redhat.com">Jitka Kudrnacova</a>
 */
public class MessageSizeLimitTest  {

    private static Endpoint endpoint;
    private Channel clientChannel;
    private Channel serverChannel;

    private static AcceptingChannel<? extends ConnectedStreamChannel> streamServer;
    private static Registration registration;
    private Connection connection;
    private Registration serviceRegistration;

    private static final Logger logger = Logger.getLogger(MessageSizeLimitTest.class);

    public static final Long MY_OUTBOUND_MSG_MAX_SIZE = 10L;
    public static final Long MY_INBOUND_MSG_MAX_SIZE = 8L;

    @BeforeClass
    public static void create() throws IOException {
        endpoint = Remoting.createEndpoint("test", OptionMap.EMPTY);
        registration = endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        SimpleServerAuthenticationProvider provider = new SimpleServerAuthenticationProvider();
        provider.addUser("bob", "test", "pass".toCharArray());
        streamServer = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123), OptionMap.create(Options.SASL_MECHANISMS, Sequence.of("CRAM-MD5")), provider, null);
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

        IoFuture<Connection> futureConnection = endpoint.connect(new URI("remote://localhost:30123"), OptionMap.EMPTY, "bob", "test", "pass".toCharArray());
        connection = futureConnection.get();
        final OptionMap myOptionMap = OptionMap.create(RemotingOptions.MAX_INBOUND_MESSAGE_SIZE, MY_INBOUND_MSG_MAX_SIZE, RemotingOptions.MAX_OUTBOUND_MESSAGE_SIZE, MY_OUTBOUND_MSG_MAX_SIZE);
        IoFuture<Channel> futureChannel = connection.openChannel("org.jboss.test", myOptionMap);


        clientChannel = futureChannel.get();
        serverChannel = passer.getIoFuture().get();
        clientChannel.getOption(RemotingOptions.MAX_OUTBOUND_MESSAGE_SIZE);

        Long inboundMax = clientChannel.getOption(RemotingOptions.MAX_INBOUND_MESSAGE_SIZE);
        assertTrue(clientChannel.supportsOption(RemotingOptions.MAX_INBOUND_MESSAGE_SIZE));
        Long outboundMax = clientChannel.getOption(RemotingOptions.MAX_OUTBOUND_MESSAGE_SIZE);
        assertEquals("Settings for inbound message size maximum are not properly set.", inboundMax, MY_INBOUND_MSG_MAX_SIZE);
        assertEquals("Setting for outbound message size maximum are not properly set.", outboundMax, MY_OUTBOUND_MSG_MAX_SIZE);
        assertNotNull(serverChannel);
        assertEquals("bob", serverChannel.getConnection().getUserInfo().getUserName());
    }

    @Test
    public void testMessageSizeLimitOK() throws Exception {
        MessageOutputStream myOutputStream = null;
        myOutputStream = clientChannel.writeMessage();
        byte[] msg = new byte[] {0,1,2,3,4,5,6,7};
        myOutputStream.write(msg); //this has 8 bytes, should pass
        myOutputStream.close();
        final CountDownLatch latch = new CountDownLatch(1);
        final ArrayList<Byte> receivedMsg = new ArrayList<Byte>();
        final ArrayList<Exception> exceptions = new ArrayList<Exception>();

        serverChannel.receiveMessage(new Channel.Receiver() {
            @Override
            public void handleError(Channel channel, IOException error) {
                error.printStackTrace();
                latch.countDown();

            }

            @Override
            public void handleEnd(Channel channel) {
                logger.info("End of channel");
                latch.countDown();
            }

            @Override
            public void handleMessage(Channel channel, MessageInputStream message) {

                logger.info("testMessageSizeLimitOK: Message received");
                try {
                    int i = message.read();
                    while (i != -1) {
                        receivedMsg.add((byte)i);
                        i = message.read();
                    }

                    message.close();
                } catch (IOException e) {
                    exceptions.add(e);
                } finally {
                    IoUtils.safeClose(message);
                    latch.countDown();
                }
            }
        });
        latch.await();
        assertEquals("Unexpected exception while writing message within limits", 0, exceptions.size());
        Byte[] received = receivedMsg.toArray(new Byte[receivedMsg.size()]);
        Byte[] sent = new Byte[msg.length];
        for (int i= 0; i<msg.length; i++) {
            sent[i] = msg[i];
        }

        assertArrayEquals(received, sent);

    }

    @Test
    public void testMessageSizeLimitOutboundOverflow() throws IOException, InterruptedException {
        MessageOutputStream myOutputStream = null;
        myOutputStream = clientChannel.writeMessage();
        byte[] msg = {0,1,2,3,4,5,6,7,8,9,1,1};
        final CountDownLatch latch = new CountDownLatch(1);
        final ArrayList<Byte> receivedMsg = new ArrayList<Byte>();

        int exceptionCount = 0;
        try {
            myOutputStream.write(msg); //this will throw exception
        }
        catch (IOException e) {
            //OK, I have created message overflow
            exceptionCount++;
            logger.info("Caught an expected exception when writing message.");
            e.printStackTrace();

        }
        finally {
            clientChannel.writeShutdown();

        }
        myOutputStream.close();

        // server channel should not get any data,
        serverChannel.receiveMessage(new Channel.Receiver() {
            @Override
            public void handleError(Channel channel, IOException error) {
                error.printStackTrace();
                latch.countDown();
                Assert.fail("I should have received message size overrun.");
            }

            @Override
            public void handleEnd(Channel channel) {
                latch.countDown();
            }

            @Override
            public void handleMessage(Channel channel, MessageInputStream message) {

                try {
                    int i = message.read();
                    while (i != -1) {
                        receivedMsg.add((byte)i);
                        i = message.read();
                    }
                    message.close();
                    Assert.fail("I should have received message size overrun.");
                } catch (IOException e) {

                } finally {
                    IoUtils.safeClose(message);
                    latch.countDown();
                    Assert.fail("I should have received message size overrun.");
                }
            }
        });
        latch.await();
        assertEquals("I have expected only exception on the client side.", 1, exceptionCount);
        assertEquals(receivedMsg.size(), 0); // no data transfered
        assertNotNull(connection);

        }

    @After
    public void afterTest() {
        IoUtils.safeClose(clientChannel);
        IoUtils.safeClose(serverChannel);
        IoUtils.safeClose(connection);
        serviceRegistration.close();
    }

    @AfterClass
    public static void destroy() throws IOException, InterruptedException {
        IoUtils.safeClose(streamServer);
        IoUtils.safeClose(endpoint);
        IoUtils.safeClose(registration);
    }
}
