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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.Closeable;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslServerFactory;

import org.jboss.logging.Logger;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.MessageCancelledException;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.auth.realm.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.realm.SimpleRealmEntry;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.permission.PermissionVerifier;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.wildfly.security.sasl.util.ServiceLoaderSaslServerFactory;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoFuture.Status;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class ChannelTestBase {

    private static final int TEST_FILE_LENGTH = 20480;
    protected Channel sendChannel;
    protected Channel recvChannel;
    private static String providerName;

    // Managed by "Create"
    protected static Endpoint endpoint;
    private static Closeable streamServer;
    // Managed by "testStart"
    private Connection connection;
    private Registration serviceRegistration;

    @BeforeClass
    public static void doBeforeClass() {
        final WildFlyElytronProvider provider = new WildFlyElytronProvider();
        Security.addProvider(provider);
        providerName = provider.getName();
    }

    protected static void create(OptionMap optionMap, String saslMech, SSLContext serverContext) throws Exception {
        // Initial Remoting Initialisation
        endpoint = Endpoint.builder().setEndpointName("test").build();
        NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);

        // WildFly Elytron Security Domain and SASL Authentication Factory
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
        domainBuilder.addRealm("mainRealm", mainRealm).build();
        domainBuilder.setDefaultRealmName("mainRealm");
        domainBuilder.setPermissionMapper((permissionMappable, roles) -> PermissionVerifier.ALL);
        final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
        mainRealm.setIdentityMap(Collections.singletonMap("bob", new SimpleRealmEntry(
            Collections.singletonList(
                new PasswordCredential(passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())))))
        ));
        final SaslServerFactory saslServerFactory = new ServiceLoaderSaslServerFactory(RemoteSslChannelTest.class.getClassLoader());
        // TODO REM3-414 We need to move to support the non-deprecated variant of SaslAuthenticationFactory.
        final SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder();
        builder.setSecurityDomain(domainBuilder.build());
        builder.setFactory(saslServerFactory);
        builder.setMechanismConfigurationSelector(mechanismInformation -> saslMech.equals(mechanismInformation.getMechanismName()) ? MechanismConfiguration.EMPTY : null);
        final SaslAuthenticationFactory saslAuthenticationFactory = builder.build();

        // Final Remoting Initialisation
        streamServer = networkServerProvider.createServer(new InetSocketAddress("localhost", 30123),
                optionMap, saslAuthenticationFactory, serverContext);
    }

    @AfterClass
    public static void doAfterClass() {
        IoUtils.safeClose(streamServer);
        IoUtils.safeClose(endpoint);

        Security.removeProvider(providerName);

        //some environments seem to need a small delay to re-bind the socket
        // we could alternatively shutdown the xnio worker, this would guarantee
        // that all tasks were fully completely for closing, but the worker is not
        // reachable from outside the remoting api
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            //ignore
        }
    }

    @Rule
    public TestName name = new TestName();

    @Before
    public void doBefore() {
        System.gc();
        System.runFinalization();
        Logger.getLogger("TEST").infof("Running test %s", name.getMethodName());
    }

    public void testStart(String saslMech, SSLContext clientContext, OptionMap optionMap) throws IOException, URISyntaxException, InterruptedException, GeneralSecurityException {
        final FutureResult<Channel> passer = new FutureResult<Channel>();
        serviceRegistration = endpoint.registerService("org.jboss.test", new OpenListener() {
            public void channelOpened(final Channel channel) {
                passer.setResult(channel);
            }

            public void registrationTerminated() {
            }
        }, OptionMap.EMPTY);


        AuthenticationContext authenticationContext = AuthenticationContext.empty()
            .with(MatchRule.ALL, AuthenticationConfiguration.empty()
                .useName("bob")
                .usePassword("pass")
                .setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism(saslMech)));

        if (clientContext != null) {
            authenticationContext = authenticationContext.withSsl(MatchRule.ALL, () -> clientContext);
        }
        IoFuture<Connection> futureConnection = authenticationContext.run(new PrivilegedAction<IoFuture<Connection>>() {
            public IoFuture<Connection> run() {
                try {
                    return endpoint.connect(new URI("remote://localhost:30123"), optionMap);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        assertEquals(Status.DONE, futureConnection.await(500, TimeUnit.MILLISECONDS)); // Some situations cause indefinate hang.
        connection = futureConnection.get();
        if (clientContext != null) {
            assertNotNull("SSLSession Available", connection.getSslSession());
        } else {
            assertNull("SSLSession Available", connection.getSslSession());
        }

        IoFuture<Channel> futureChannel = connection.openChannel("org.jboss.test", OptionMap.EMPTY);
        sendChannel = futureChannel.get();
        recvChannel = passer.getIoFuture().get();
        assertNotNull(recvChannel);
        if (clientContext != null) {
            assertNotNull("SSLSession Available", recvChannel.getConnection().getSslSession());
        } else {
            assertNull("SSLSession Available", recvChannel.getConnection().getSslSession());
        }
    }

    @After
    public void doAfter() {
        IoUtils.safeClose(sendChannel);
        IoUtils.safeClose(recvChannel);
        IoUtils.safeClose(connection);
        serviceRegistration.close();

        System.gc();
        System.runFinalization();
        Logger.getLogger("TEST").infof("Finished test %s", name.getMethodName());
    }

    @Test
    public void testEmptyMessage() throws IOException, InterruptedException {
        final AtomicBoolean wasEmpty = new AtomicBoolean();
        final AtomicReference<IOException> exRef = new AtomicReference<IOException>();
        final CountDownLatch latch = new CountDownLatch(1);
        recvChannel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                error.printStackTrace();
                exRef.set(error);
                latch.countDown();
            }

            public void handleEnd(final Channel channel) {
                System.out.println("End of channel");
                latch.countDown();
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                System.out.println("Message received");
                try {
                    if (message.read() == -1) {
                        wasEmpty.set(true);
                    }
                    message.close();
                } catch (IOException e) {
                    exRef.set(e);
                } finally {
                    IoUtils.safeClose(message);
                    latch.countDown();
                }
            }
        });
        MessageOutputStream messageOutputStream = sendChannel.writeMessage();
        messageOutputStream.close();
        latch.await();
        IOException exception = exRef.get();
        if (exception != null) {
            throw exception;
        }
        assertTrue(wasEmpty.get());
    }

    @Test
    public void testLotsOfContent() throws IOException, InterruptedException {
        final AtomicBoolean wasOk = new AtomicBoolean();
        final AtomicReference<IOException> exRef = new AtomicReference<IOException>();
        final CountDownLatch latch = new CountDownLatch(1);
        InputStream stream = ChannelTestBase.class.getResourceAsStream("/test-content.bin");
        assertNotNull(stream);
        final byte[] data;
        try {
            data = new byte[TEST_FILE_LENGTH];
            int c = 0;
            do {
                int r = stream.read(data, c, TEST_FILE_LENGTH - c);
                if (r == -1) {
                    break;
                }
                c += r;
            } while (c < TEST_FILE_LENGTH);
            stream.close();
        } finally {
            IoUtils.safeClose(stream);
        }

        recvChannel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                error.printStackTrace();
                exRef.set(error);
                latch.countDown();
            }

            public void handleEnd(final Channel channel) {
                System.out.println("End of channel");
                latch.countDown();
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            System.out.println("Message received");
                            final byte[] received = new byte[TEST_FILE_LENGTH];
                            int c = 0;
                            do {
                                int r = message.read(received, c, TEST_FILE_LENGTH - c);
                                if (r == -1) {
                                    break;
                                }
                                c += r;
                            } while (c < TEST_FILE_LENGTH);
                            message.close();

                            assertArrayEquals(data, received);
                            wasOk.set(true);
                        } catch (IOException e) {
                            exRef.set(e);
                        } finally {
                            IoUtils.safeClose(message);
                            latch.countDown();
                        }
                    }
                }).start();
            }
        });
        MessageOutputStream messageOutputStream = sendChannel.writeMessage();
        messageOutputStream.write(data);
        messageOutputStream.close();
        messageOutputStream.close(); // close should be idempotent
        messageOutputStream.flush(); // no effect expected, since message is closed
        messageOutputStream.flush();
        messageOutputStream.flush();
        latch.await();
        IOException exception = exRef.get();
        if (exception != null) {
            throw exception;
        }
        assertTrue(wasOk.get());
    }

    @Test
    public void testWriteCancel() throws IOException, InterruptedException {
        InputStream stream = ChannelTestBase.class.getResourceAsStream("/test-content.bin");
        assertNotNull(stream);
        final byte[] data;
        try {
            data = new byte[TEST_FILE_LENGTH];
            int c = 0;
            do {
                int r = stream.read(data, c, TEST_FILE_LENGTH - c);
                if (r == -1) {
                    break;
                }
                c += r;
            } while (c < TEST_FILE_LENGTH);
            stream.close();
        } finally {
            IoUtils.safeClose(stream);
        }
        testWriteCancel(data);
    }

    @Test
    public void testWriteCancelIncompleteMessage() throws IOException, InterruptedException {
        InputStream stream = ChannelTestBase.class.getResourceAsStream("/test-content.bin");
        assertNotNull(stream);
        final byte[] data;
        try {
            data = new byte[TEST_FILE_LENGTH/2];
            int c = 0;
            do {
                int r = stream.read(data, c, TEST_FILE_LENGTH/2 - c);
                if (r == -1) {
                    break;
                }
                c += r;
            } while (c < TEST_FILE_LENGTH/2);
            stream.close();
        } finally {
            IoUtils.safeClose(stream);
        }
        testWriteCancel(data);
    }

    public void testWriteCancel(final byte[] data) throws IOException, InterruptedException {
        final AtomicBoolean wasOk = new AtomicBoolean();
        final AtomicReference<IOException> exRef = new AtomicReference<IOException>();
        final CountDownLatch latch = new CountDownLatch(1);
        recvChannel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                error.printStackTrace();
                exRef.set(error);
                latch.countDown();
            }

            public void handleEnd(final Channel channel) {
                System.out.println("End of channel");
                latch.countDown();
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                new Thread(new Runnable() {
                    public void run() {
                        final byte[] received = new byte[TEST_FILE_LENGTH];
                        int c = 0;
                        try {
                            System.out.println("Message received");
                            int r;
                            do {
                                r = message.read(received, c, TEST_FILE_LENGTH - c);
                                if (r == -1) {
                                    break;
                                }
                                c += r;
                            } while (c < TEST_FILE_LENGTH);
                            if (r != -1) {
                                r = message.read();
                            }
                            message.close();
                        } catch (MessageCancelledException e) {
                            System.out.println("Value of c at message cancelled is " + c);
                            int i = 0;
                            while (i < c) {
                                if (data[i] != received[i]) {
                                    break;
                                }
                                i++;
                            }
                            wasOk.set(i == c);
                        } catch (IOException e) {
                            exRef.set(e);
                        } finally {
                            IoUtils.safeClose(message);
                            latch.countDown();
                        }
                    }
                }).start();
            }
        });
        MessageOutputStream messageOutputStream = sendChannel.writeMessage();
        messageOutputStream.write(data);
        messageOutputStream.cancel();
        messageOutputStream.close();
        messageOutputStream.close(); // close should be idempotent
        messageOutputStream.flush(); // no effect expected, since message is closed
        latch.await();
        IOException exception = exRef.get();
        if (exception != null) {
            throw exception;
        }
        assertTrue(wasOk.get());
    }

    @Test
    public void testSimpleWriteMethod() throws Exception {
        Byte[] bytes = new Byte[] {1, 2, 3};
        MessageOutputStream out = sendChannel.writeMessage();
        for (int i = 0 ; i < bytes.length ; i++) {
            out.write(bytes[i]);
        }
        out.close();

        final CountDownLatch latch = new CountDownLatch(1);
        final ArrayList<Byte> result = new ArrayList<Byte>();
        final AtomicReference<IOException> exRef = new AtomicReference<IOException>();
        recvChannel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                error.printStackTrace();
                latch.countDown();
            }

            public void handleEnd(final Channel channel) {
                System.out.println("End of channel");
                latch.countDown();
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                System.out.println("Message received");
                try {
                    int i = message.read();
                    while (i != -1) {
                        result.add((byte)i);
                        i = message.read();
                    }
                    message.close();
                } catch (IOException e) {
                    exRef.set(e);
                } finally {
                    IoUtils.safeClose(message);
                    latch.countDown();
                }
            }
        });

        latch.await();
        assertNull(exRef.get());
        Byte[] resultBytes = result.toArray(new Byte[result.size()]);
        assertArrayEquals(bytes, resultBytes);
    }

    @Test
    public void testSimpleWriteMethodWithWrappedOuputStream() throws Exception {
        Byte[] bytes = new Byte[] {1, 2, 3};

        FilterOutputStream out = new FilterOutputStream(sendChannel.writeMessage());
        for (int i = 0 ; i < bytes.length ; i++) {
            out.write(bytes[i]);
        }
        //The close() method of FilterOutputStream will flush the underlying output stream before closing it,
        //so we end up with two messages
        out.close();

        final CountDownLatch latch = new CountDownLatch(1);
        final ArrayList<Byte> result = new ArrayList<Byte>();
        final AtomicReference<IOException> exRef = new AtomicReference<IOException>();
        recvChannel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                error.printStackTrace();
                latch.countDown();
            }

            public void handleEnd(final Channel channel) {
                System.out.println("End of channel");
                latch.countDown();
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                System.out.println("Message received");
                try {
                    int i = message.read();
                    while (i != -1) {
                        result.add((byte)i);
                        i = message.read();
                    }
                    message.close();
                } catch (IOException e) {
                    exRef.set(e);
                } finally {
                    IoUtils.safeClose(message);
                    latch.countDown();
                }
            }
        });

        latch.await();
        assertNull(exRef.get());
        Byte[] resultBytes = result.toArray(new Byte[result.size()]);
        assertArrayEquals(bytes, resultBytes);
    }

    @Test
    public void testSimpleWriteMethodFromNonInitiatingSide() throws Exception {
        Byte[] bytes = new Byte[] {1, 2, 3};
        MessageOutputStream out = recvChannel.writeMessage();
        for (int i = 0 ; i < bytes.length ; i++) {
            out.write(bytes[i]);
        }
        out.close();

        final CountDownLatch latch = new CountDownLatch(1);
        final ArrayList<Byte> result = new ArrayList<Byte>();
        final AtomicReference<IOException> exRef = new AtomicReference<IOException>();
        sendChannel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                error.printStackTrace();
                latch.countDown();
            }

            public void handleEnd(final Channel channel) {
                System.out.println("End of channel");
                latch.countDown();
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                System.out.println("Message received");
                try {
                    int i = message.read();
                    while (i != -1) {
                        result.add((byte)i);
                        i = message.read();
                    }
                    message.close();
                } catch (IOException e) {
                    exRef.set(e);
                } finally {
                    IoUtils.safeClose(message);
                    latch.countDown();
                }
            }
        });
        latch.await();
        assertNull(exRef.get());
        Byte[] resultBytes = result.toArray(new Byte[result.size()]);
        assertArrayEquals(bytes, resultBytes);
    }

    @Test
    public void testSimpleWriteMethodTwoWay() throws Exception {

        Byte[] bytes = new Byte[] {1, 2, 3};
        Byte[] manipulatedBytes = new Byte[] {2, 4, 6};
        MessageOutputStream out = sendChannel.writeMessage();
        for (int i = 0 ; i < bytes.length ; i++) {
            out.write(bytes[i]);
        }
        out.close();

        final CountDownLatch latch = new CountDownLatch(2);
        final ArrayList<Byte> senderResult = new ArrayList<Byte>();
        final ArrayList<Byte> receiverResult = new ArrayList<Byte>();
        final AtomicReference<IOException> exRef = new AtomicReference<IOException>();
        recvChannel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                error.printStackTrace();
                latch.countDown();
            }

            public void handleEnd(final Channel channel) {
                System.out.println("End of channel");
                latch.countDown();
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                System.out.println("Message received on receiver");
                try {
                    int i = message.read();
                    while (i != -1) {
                        receiverResult.add((byte)i);
                        System.out.println("read " + i);
                        i = message.read();
                    }
                    message.close();
                    MessageOutputStream out = channel.writeMessage();
                    try {
                        for (Byte b : receiverResult) {
                            byte send = (byte)(b * 2);
                            System.out.println("Sending back " + send);
                            out.write(send);
                        }
                    } finally {
                        out.close();
                        out.close(); // close should be idempotent
                        out.flush(); // no effect expected, since message is closed
                    }
                    System.out.println("Done writing");
                } catch (IOException e) {
                    exRef.set(e);
                } finally {
                    IoUtils.safeClose(message);
                    latch.countDown();
                }
            }
        });
        sendChannel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                error.printStackTrace();
                latch.countDown();
            }

            public void handleEnd(final Channel channel) {
                System.out.println("End of channel");
                latch.countDown();
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                System.out.println("Message received on sender");
                try {
                    int i = message.read();
                    while (i != -1) {
                        senderResult.add((byte)i);
                        i = message.read();
                    }
                    message.close();
                } catch (IOException e) {
                    exRef.set(e);
                } finally {
                    IoUtils.safeClose(message);
                    latch.countDown();
                }
            }
        });

        latch.await();
        assertNull(exRef.get());
        Byte[] receiverBytes = receiverResult.toArray(new Byte[receiverResult.size()]);
        assertArrayEquals(bytes, receiverBytes);
        Byte[] senderBytes = senderResult.toArray(new Byte[senderResult.size()]);
        assertArrayEquals(manipulatedBytes, senderBytes);
    }

    @Test
    public void testSeveralWriteMessage() throws Exception {
        final AtomicBoolean wasEmpty = new AtomicBoolean();
        final AtomicReference<IOException> exRef = new AtomicReference<IOException>();
        final CountDownLatch latch = new CountDownLatch(50);
        final AtomicInteger count = new AtomicInteger();
        recvChannel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                error.printStackTrace();
                exRef.set(error);
                latch.countDown();
            }

            public void handleEnd(final Channel channel) {
                System.out.println("End of channel");
                latch.countDown();
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                System.out.println("Message received");
                try {
                    if (message.read() == -1) {
                        wasEmpty.set(true);
                    }
                    message.close();
                } catch (IOException e) {
                    exRef.set(e);
                } finally {
                    IoUtils.safeClose(message);
                    latch.countDown();
                    if (count.getAndIncrement() < 50) {
                        recvChannel.receiveMessage(this);
                    }
                }
            }
        });
        for (int i = 0 ; i < 50 ; i++) {
            MessageOutputStream messageOutputStream = sendChannel.writeMessage();
            messageOutputStream.close();
            messageOutputStream.close(); // close should be idempotent
            messageOutputStream.flush(); // no effect expected, since message is closed
        }
        latch.await();
        IOException exception = exRef.get();
        if (exception != null) {
            throw exception;
        }
        assertTrue(wasEmpty.get());
    }

    @Test
    public void testRemoteChannelClose() throws Exception {
        final CountDownLatch closedLatch = new CountDownLatch(1);
        sendChannel.addCloseHandler(new CloseHandler<Channel>() {
            public void handleClose(final Channel closed, final IOException exception) {
                closedLatch.countDown();
            }
        });
        sendChannel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                channel.closeAsync();
            }

            public void handleEnd(final Channel channel) {
                channel.closeAsync();
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                IoUtils.safeClose(message);
            }
        });
        recvChannel.receiveMessage(new Channel.Receiver() {
            public void handleError(final Channel channel, final IOException error) {
                channel.closeAsync();
            }

            public void handleEnd(final Channel channel) {
                channel.closeAsync();
            }

            public void handleMessage(final Channel channel, final MessageInputStream message) {
                IoUtils.safeClose(message);
            }
        });
        sendChannel.writeShutdown();
        IoUtils.safeClose(recvChannel);
        System.out.println("Waiting for closed");
        closedLatch.await();
        System.out.println("Closed");
    }
}
