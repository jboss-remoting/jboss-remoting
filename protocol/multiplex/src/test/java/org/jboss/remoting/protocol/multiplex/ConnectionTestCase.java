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

package org.jboss.remoting.protocol.multiplex;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.io.IOException;
import junit.framework.TestCase;
import org.jboss.remoting.core.EndpointImpl;
import org.jboss.remoting.test.support.LoggingHelper;
import org.jboss.remoting.LocalServiceConfiguration;
import org.jboss.remoting.RequestListener;
import org.jboss.remoting.ClientContext;
import org.jboss.remoting.ServiceContext;
import org.jboss.remoting.RequestContext;
import org.jboss.remoting.RemoteExecutionException;
import org.jboss.remoting.ClientSource;
import org.jboss.remoting.Client;
import org.jboss.remoting.spi.QualifiedName;
import org.jboss.remoting.spi.NamedServiceRegistry;
import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.remoting.spi.Handle;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.CloseableExecutor;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.channels.AllocatedMessageChannel;
import org.jboss.xnio.channels.Channels;
import org.jboss.xnio.nio.NioXnio;
import org.jboss.river.RiverMarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;

/**
 *
 */
public final class ConnectionTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    public static final Logger log = Logger.getLogger(ConnectionTestCase.class.getSimpleName());

    public void testConnection() throws Throwable {
        final String REQUEST = "request";
        final String REPLY = "reply";
        final List<Throwable> problems = Collections.synchronizedList(new LinkedList<Throwable>());
        final CloseableExecutor closeableExecutor = IoUtils.closeableExecutor(Executors.newCachedThreadPool(), 500L, TimeUnit.MILLISECONDS);
        try {
            final BufferAllocator<ByteBuffer> allocator = Buffers.createHeapByteBufferAllocator(1024);
            final Xnio xnio = NioXnio.create();
            try {
                final EndpointImpl remoteEndpoint = new EndpointImpl(closeableExecutor, "left-side");
                try {
                    final EndpointImpl endpoint = new EndpointImpl(closeableExecutor, "right-side");
                    try {
                        final CountDownLatch latch = new CountDownLatch(1);
                        final MultiplexConfiguration configuration = new MultiplexConfiguration();
                        configuration.setAllocator(allocator);
                        configuration.setExecutor(closeableExecutor);
                        configuration.setLinkMetric(10);
                        configuration.setMarshallerFactory(new RiverMarshallerFactory());
                        final NamedServiceRegistry registry = new NamedServiceRegistry();
                        configuration.setNamedServiceRegistry(registry);
                        final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
                        configuration.setMarshallingConfiguration(marshallingConfiguration);
                        final LocalServiceConfiguration<Object, Object> localServiceConfiguration = new LocalServiceConfiguration<Object, Object>(new RequestListener<Object, Object>() {
                            public void handleClientOpen(final ClientContext context) {
                                log.debug("Client open");
                            }

                            public void handleServiceOpen(final ServiceContext context) {
                            }

                            public void handleRequest(final RequestContext<Object> context, final Object request) throws RemoteExecutionException {
                                try {
                                    context.sendReply(REPLY);
                                } catch (IOException e) {
                                    log.error(e, "Failed to send reply");
                                    problems.add(e);
                                }
                            }

                            public void handleServiceClose(final ServiceContext context) {
                            }

                            public void handleClientClose(final ClientContext context) {
                                log.debug("Client closed");
                                latch.countDown();
                            }

                            public String toString() {
                                return "TestListener";
                            }
                        }, Object.class, Object.class);
                        localServiceConfiguration.setServiceType("connection.test");
                        localServiceConfiguration.setGroupName("testgroup");
                        localServiceConfiguration.setMetric(10);
                        final Handle<RequestHandlerSource> requestHandlerSourceHandle = remoteEndpoint.registerService(localServiceConfiguration);
                        try {
                            registry.registerService(QualifiedName.parse("/test/connectiontest"), requestHandlerSourceHandle.getResource());
                            final IoHandlerFactory<AllocatedMessageChannel> handlerFactory = MultiplexProtocol.createServer(remoteEndpoint, configuration);
                            final ChannelSource<AllocatedMessageChannel> channelSource = Channels.convertStreamToAllocatedMessage(xnio.createPipeServer(Channels.convertStreamToAllocatedMessage(handlerFactory, 16384, 16384)), 16384, 16384);
                            final IoFuture<MultiplexConnection> future = MultiplexProtocol.connect(endpoint, configuration, channelSource);
                            final MultiplexConnection connection = future.get();
                            try {
                                final Handle<RequestHandlerSource> remoteHandlerSource = connection.openRemoteService(QualifiedName.parse("/test/connectiontest"));
                                try {
                                    final ClientSource<Object, Object> clientSource = endpoint.createClientSource(remoteHandlerSource.getResource(), Object.class, Object.class);
                                    try {
                                        final Client<Object,Object> client = clientSource.createClient();
                                        try {
                                            final IoFuture<Object> futureReply = client.send(REQUEST);
                                            assertEquals(IoFuture.Status.DONE, futureReply.await(1L, TimeUnit.SECONDS));
                                            assertEquals(REPLY, futureReply.get());
                                            client.close();
                                            clientSource.close();
                                            remoteHandlerSource.close();
                                            connection.close();
                                            assertTrue(latch.await(1L, TimeUnit.SECONDS));
                                        } finally {
                                            IoUtils.safeClose(client);
                                        }
                                    } finally {
                                        IoUtils.safeClose(clientSource);
                                    }
                                } finally {
                                    IoUtils.safeClose(remoteHandlerSource);
                                }
                            } finally {
                                IoUtils.safeClose(connection);
                            }
                        } finally {
                            IoUtils.safeClose(requestHandlerSourceHandle);
                        }
                    } finally {
                        IoUtils.safeClose(endpoint);
                    }
                } finally {
                    IoUtils.safeClose(remoteEndpoint);
                }
            } finally {
                IoUtils.safeClose(xnio);
            }
        } finally {
            IoUtils.safeClose(closeableExecutor);
        }
        for (Throwable t : problems) {
            throw t;
        }
    }
}
