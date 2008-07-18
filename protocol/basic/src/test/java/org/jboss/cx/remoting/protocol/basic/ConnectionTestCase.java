/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.cx.remoting.protocol.basic;

import junit.framework.TestCase;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.ConfigurableFactory;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.TcpClient;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.channels.Channels;
import org.jboss.xnio.channels.AllocatedMessageChannel;
import org.jboss.cx.remoting.core.EndpointImpl;
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.ClientContext;
import org.jboss.cx.remoting.ServiceContext;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.test.support.LoggingHelper;
import org.jboss.cx.remoting.spi.remote.RemoteServiceEndpoint;
import org.jboss.cx.remoting.spi.remote.RemoteClientEndpointListener;
import org.jboss.cx.remoting.spi.remote.Handle;
import org.jboss.cx.remoting.spi.remote.RemoteClientEndpoint;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.io.Closeable;

/**
 *
 */
public final class ConnectionTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    public void testConnection() throws Throwable {
        final List<Throwable> problems = Collections.synchronizedList(new LinkedList<Throwable>());
        final AtomicBoolean clientOpened = new AtomicBoolean(false);
        final AtomicBoolean client2Opened = new AtomicBoolean(false);
        final AtomicBoolean serviceOpened = new AtomicBoolean(false);
        final AtomicBoolean clientClosed = new AtomicBoolean(false);
        final AtomicBoolean serviceClosed = new AtomicBoolean(false);
        final CountDownLatch cleanupLatch = new CountDownLatch(2);
        final ExecutorService executorService = Executors.newCachedThreadPool();
        try {
            final BufferAllocator<ByteBuffer> allocator = new BufferAllocator<ByteBuffer>() {
                public ByteBuffer allocate() {
                    return ByteBuffer.allocate(1024);
                }

                public void free(final ByteBuffer buffer) {
                }
            };
            final Xnio xnio = Xnio.createNio();
            try {
                final EndpointImpl endpoint = new EndpointImpl();
                endpoint.setExecutor(executorService);
                endpoint.start();
                try {
                    final RemoteServiceEndpoint serverServiceEndpoint = endpoint.createServiceEndpoint(new RequestListener<Object, Object>() {
                        public void handleClientOpen(final ClientContext context) {
                            if (clientOpened.getAndSet(true)) {
                                if (client2Opened.getAndSet(true)) {
                                    problems.add(new IllegalStateException("Too many client opens"));
                                }
                            }
                        }

                        public void handleServiceOpen(final ServiceContext context) {
                            if (serviceOpened.getAndSet(true)) {
                                problems.add(new IllegalStateException("Multiple service opens"));
                            }
                        }

                        public void handleRequest(final RequestContext<Object> context, final Object request) throws RemoteExecutionException {
                            try {
                                System.out.println("Received request; sending response!");
                                context.sendReply("response");
                            } catch (RemotingException e) {
                                try {
                                    context.sendFailure("failed", e);
                                } catch (RemotingException e1) {
                                    problems.add(e1);
                                }
                            }
                        }

                        public void handleServiceClose(final ServiceContext context) {
                            if (serviceClosed.getAndSet(true)) {
                                problems.add(new IllegalStateException("Multiple service closes"));
                            }
                            cleanupLatch.countDown();
                        }

                        public void handleClientClose(final ClientContext context) {
                            if (clientClosed.getAndSet(true)) {
                                problems.add(new IllegalStateException("Multiple client closes"));
                            }
                            cleanupLatch.countDown();
                        }
                    });
                    try {
                        final Handle<RemoteServiceEndpoint> serviceHandle = serverServiceEndpoint.getHandle();
                        serverServiceEndpoint.autoClose();
                        try {
                            final RemoteClientEndpointListener remoteListener = new RemoteClientEndpointListener() {
                                public <I, O> void notifyCreated(final RemoteClientEndpoint endpoint) {
                                }
                            };
                            final ConfigurableFactory<Closeable> tcpServer = xnio.createTcpServer(executorService, Channels.convertStreamToAllocatedMessage(BasicProtocol.createServer(executorService, serverServiceEndpoint, allocator, remoteListener), 32768, 32768), new InetSocketAddress(12345));
                            final Closeable tcpServerCloseable = tcpServer.create();
                            try {
                                // now create a client to connect to it
                                final RemoteClientEndpoint localRoot = ;
                                try {
                                    serverServiceEndpoint.autoClose();
                                    final InetSocketAddress destAddr = new InetSocketAddress("localhost", 12345);
                                    final TcpClient tcpClient = xnio.createTcpConnector().create().createChannelSource(destAddr);
                                    try {
                                        final ChannelSource<AllocatedMessageChannel> messageChannelSource = Channels.convertStreamToAllocatedMessage(tcpClient, 32768, 32768);
                                        final IoFuture<RemoteClientEndpoint> futureClient = BasicProtocol.connect(executorService, localRoot, messageChannelSource, allocator);
                                        final RemoteClientEndpoint clientEndpoint = futureClient.get();
                                        try {
                                            final Client<Object,Object> client = endpoint.createClient(clientEndpoint);
                                            try {
                                                clientEndpoint.autoClose();
                                                final Object result = client.send("Test").get();
                                                assertEquals("response", result);
                                                client.close();
                                                cleanupLatch.await(500L, TimeUnit.MILLISECONDS);
                                                tcpServerCloseable.close();
                                                serviceHandle.close();
                                                assertTrue(serviceOpened.get());
                                                assertTrue(clientOpened.get());
                                                assertTrue(client2Opened.get());
                                                assertTrue(clientClosed.get());
                                                assertTrue(serviceClosed.get());
                                            } finally {
                                                IoUtils.safeClose(client);
                                            }
                                        } finally {
                                            IoUtils.safeClose(clientEndpoint);
                                        }
                                    } finally {
                                        // todo close tcpClient
                                    }
                                } finally {
                                    IoUtils.safeClose(localRoot);
                                }
                            } finally {
                                IoUtils.safeClose(tcpServerCloseable);
                            }
                        } finally {
                            IoUtils.safeClose(serviceHandle);
                        }
                    } finally {
                        IoUtils.safeClose(serverServiceEndpoint);
                    }
                } finally {
                    endpoint.stop();
                }
            } finally {
                IoUtils.safeClose(xnio);
            }
        } finally {
            executorService.shutdownNow();
        }
        for (Throwable t : problems) {
            throw t;
        }
    }
}
