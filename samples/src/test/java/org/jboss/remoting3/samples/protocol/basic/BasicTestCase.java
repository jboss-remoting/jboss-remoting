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

package org.jboss.remoting3.samples.protocol.basic;

import junit.framework.TestCase;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.nio.NioXnio;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.AbstractRequestListener;
import org.jboss.remoting3.RequestContext;
import org.jboss.remoting3.RemoteExecutionException;
import org.jboss.remoting3.Client;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.Handle;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.river.RiverMarshallerFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.io.IOException;

/**
 *
 */
public final class BasicTestCase extends TestCase {

    public static void testConnect() throws Throwable {
        ExecutorService executor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        try {
            Xnio xnio = NioXnio.create(executor, 2, 2, 2);
            try {
                final BasicConfiguration configuration = new BasicConfiguration();
                configuration.setExecutor(executor);
                configuration.setMarshallerFactory(new RiverMarshallerFactory());
                final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
                configuration.setMarshallingConfiguration(marshallingConfiguration);
                final Endpoint endpoint = Remoting.createEndpoint(executor, "test");
                try {
                    final Handle<RequestHandler> requestHandlerHandle = endpoint.createRequestHandler(new AbstractRequestListener<Object, Object>() {
                        public void handleRequest(final RequestContext<Object> context, final Object request) throws RemoteExecutionException {
                            System.out.println("Got a request! " + request.toString());
                            try {
                                context.sendReply("GOOMBA");
                            } catch (IOException e) {
                                try {
                                    context.sendFailure("Failed", e);
                                } catch (IOException e1) {
                                    // buh
                                }
                            }
                        }
                    }, Object.class, Object.class);
                    try {
                        final ChannelSource<StreamChannel> channelSource = xnio.createPipeServer(executor, IoUtils.singletonHandlerFactory(new IoHandler<StreamChannel>() {
                            public void handleOpened(final StreamChannel channel) {
                                try {
                                    System.out.println("Opening channel");
                                    BasicProtocol.createServer(requestHandlerHandle, channel, configuration);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    IoUtils.safeClose(channel);
                                }
                            }

                            public void handleReadable(final StreamChannel channel) {
                            }

                            public void handleWritable(final StreamChannel channel) {
                            }

                            public void handleClosed(final StreamChannel channel) {
                                System.out.println("Closing channel");
                            }
                        }));
                        final IoFuture<StreamChannel> futureChannel = channelSource.open(IoUtils.nullHandler());
                        assertEquals(IoFuture.Status.DONE, futureChannel.await(1L, TimeUnit.SECONDS));
                        final StreamChannel channel = futureChannel.get();
                        try {
                            final Handle<RequestHandler> clientHandlerHandle = BasicProtocol.createClient(channel, configuration);
                            try {
                                final Client<Object,Object> client = endpoint.createClient(clientHandlerHandle.getResource(), Object.class, Object.class);
                                try {
                                    final IoFuture<? extends Object> futureReply = client.send("GORBA!");
                                    assertEquals(IoFuture.Status.DONE, futureReply.await(500L, TimeUnit.MILLISECONDS));
                                    System.out.println("Reply is:" + futureReply.get());
                                } finally {
                                    IoUtils.safeClose(client);
                                }
                            } finally {
                                IoUtils.safeClose(clientHandlerHandle);
                            }
                        } finally {
                            IoUtils.safeClose(channel);
                        }
                    } finally {
                        IoUtils.safeClose(requestHandlerHandle);
                    }
                } finally {
                    IoUtils.safeClose(endpoint);
                }
            } finally {
                IoUtils.safeClose(xnio);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
