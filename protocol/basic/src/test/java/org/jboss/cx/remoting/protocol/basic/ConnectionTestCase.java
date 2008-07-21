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

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.net.InetSocketAddress;
import java.io.Closeable;
import junit.framework.TestCase;
import org.jboss.cx.remoting.core.EndpointImpl;
import org.jboss.cx.remoting.test.support.LoggingHelper;
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.ClientSource;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.FutureReply;
import org.jboss.cx.remoting.AbstractRequestListener;
import org.jboss.cx.remoting.spi.remote.RemoteServiceEndpoint;
import org.jboss.cx.remoting.spi.remote.Handle;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.TcpClient;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.CloseableTcpConnector;
import org.jboss.xnio.CloseableExecutor;
import org.jboss.xnio.channels.AllocatedMessageChannel;
import org.jboss.xnio.channels.Channels;
import org.jboss.xnio.channels.StreamChannel;

/**
 *
 */
public final class ConnectionTestCase extends TestCase {
    static {
        LoggingHelper.init();
    }

    public void testConnection() throws Throwable {
        final String REQUEST = "request";
        final String REPLY = "reply";
        final List<Throwable> problems = Collections.synchronizedList(new LinkedList<Throwable>());
        final CloseableExecutor closeableExecutor = IoUtils.closeableExecutor(Executors.newCachedThreadPool(), 500L, TimeUnit.MILLISECONDS);
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
                endpoint.setExecutor(closeableExecutor);
                endpoint.start();
                try {
                    final ServiceRegistry serviceRegistry = new ServiceRegistryImpl();
                    try {
                        final Handle<RemoteServiceEndpoint> serviceEndpointHandle = endpoint.createServiceEndpoint(new AbstractRequestListener<Object, Object>() {
                            public void handleRequest(final RequestContext<Object> context, final Object request) throws RemoteExecutionException {
                                try {
                                    context.sendReply(REPLY);
                                } catch (RemotingException e) {
                                    problems.add(e);
                                }
                            }
                        });
                        try {
                            serviceRegistry.bind(serviceEndpointHandle.getResource(), 13);
                            final IoHandlerFactory<AllocatedMessageChannel> handlerFactory = BasicProtocol.createServer(closeableExecutor, allocator, serviceRegistry);
                            final IoHandlerFactory<StreamChannel> newHandlerFactory = Channels.convertStreamToAllocatedMessage(handlerFactory, 32768, 32768);
                            final Closeable tcpServerCloseable = xnio.createTcpServer(newHandlerFactory, new InetSocketAddress(12345)).create();
                            try {
                                final CloseableTcpConnector connector = xnio.createTcpConnector().create();
                                try {
                                    final TcpClient tcpClient = connector.createChannelSource(new InetSocketAddress("localhost", 12345));
                                    final ChannelSource<AllocatedMessageChannel> channelSource = Channels.convertStreamToAllocatedMessage(tcpClient, 32768, 32768);
                                    final IoFuture<Connection> futureCloseable = BasicProtocol.connect(closeableExecutor, channelSource, allocator, serviceRegistry);
                                    final Connection connection = futureCloseable.get();
                                    try {
                                        final Handle<RemoteServiceEndpoint> handleThirteen = connection.getServiceForId(13);
                                        try {
                                            final ClientSource<Object,Object> clientSource = endpoint.createClientSource(handleThirteen.getResource());
                                            try {
                                                final Client<Object,Object> client = clientSource.createClient();
                                                try {
                                                    final FutureReply<Object> future = client.send(REQUEST);
                                                    assertEquals(REPLY, future.get(500L, TimeUnit.MILLISECONDS));
                                                    client.close();
                                                    clientSource.close();
                                                    handleThirteen.close();
                                                    connection.close();
                                                    connector.close();
                                                    tcpServerCloseable.close();
                                                    serviceEndpointHandle.close();
                                                    serviceRegistry.clear();
                                                    endpoint.stop();
                                                    xnio.close();
                                                    closeableExecutor.close();
                                                } finally {
                                                    IoUtils.safeClose(client);
                                                }
                                            } finally {
                                                IoUtils.safeClose(clientSource);
                                            }
                                        } finally {
                                            IoUtils.safeClose(handleThirteen);
                                        }
                                    } finally {
                                        IoUtils.safeClose(connection);
                                    }
                                } finally {
                                    IoUtils.safeClose(connector);
                                }
                            } finally {
                                IoUtils.safeClose(tcpServerCloseable);
                            }
                        } finally {
                            IoUtils.safeClose(serviceEndpointHandle);
                        }
                    } finally {
                        serviceRegistry.clear();
                    }
                } finally {
                    endpoint.stop();
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
