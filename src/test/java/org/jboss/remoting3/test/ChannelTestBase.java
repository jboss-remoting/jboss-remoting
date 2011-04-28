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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageCancelledException;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.testng.annotations.Test;
import org.xnio.IoUtils;

import static org.testng.Assert.*;

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
                int r = stream.read(data);
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

                    assertEquals(data, received);
                    wasOk.set(true);
                } catch (IOException e) {
                    exRef.set(e);
                } finally {
                    IoUtils.safeClose(message);
                    latch.countDown();
                }
            }
        });
        MessageOutputStream messageOutputStream = sendChannel.writeMessage();
        messageOutputStream.write(data);
        messageOutputStream.close();
        latch.await();
        IOException exception = exRef.get();
        if (exception != null) {
            throw exception;
        }
        assertTrue(wasOk.get());
    }

    @Test
    public void testWriteCancel() throws IOException, InterruptedException {
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
                int r = stream.read(data);
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
                final byte[] received = new byte[TEST_FILE_LENGTH];
                try {
                    System.out.println("Message received");
                    int c = 0;
                    do {
                        int r = message.read(received);
                        if (r == -1) {
                            break;
                        }
                        c += r;
                    } while (c < TEST_FILE_LENGTH);
                    message.close();
                } catch (MessageCancelledException e) {
                    wasOk.set(Arrays.equals(data, received));
                } catch (IOException e) {
                    exRef.set(e);
                } finally {
                    IoUtils.safeClose(message);
                    latch.countDown();
                }
            }
        });
        MessageOutputStream messageOutputStream = sendChannel.writeMessage();
        messageOutputStream.write(data);
        messageOutputStream.cancel();
        messageOutputStream.close();
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
        assertEquals(bytes, resultBytes);
    }
    

}
