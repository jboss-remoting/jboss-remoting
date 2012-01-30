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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.MessageCancelledException;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.junit.Test;
import org.xnio.IoUtils;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class ChannelTestBase {

    private static final int TEST_FILE_LENGTH = 20480;
    protected Channel sendChannel;
    protected Channel recvChannel;

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
        final CountDownLatch latch = new CountDownLatch(100);
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
                    if (count.getAndIncrement() < 100) {
                        recvChannel.receiveMessage(this);
                    }
                }
            }
        });
        for (int i = 0 ; i < 100 ; i++) {
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
