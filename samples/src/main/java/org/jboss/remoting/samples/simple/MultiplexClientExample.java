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

package org.jboss.remoting.samples.simple;

import org.jboss.remoting.Endpoint;
import org.jboss.remoting.Remoting;
import org.jboss.remoting.ClientSource;
import org.jboss.remoting.Client;
import org.jboss.remoting.QualifiedName;
import org.jboss.remoting.multiplex.MultiplexProtocol;
import org.jboss.remoting.multiplex.MultiplexConfiguration;
import org.jboss.remoting.multiplex.MultiplexConnection;
import org.jboss.remoting.spi.NamedServiceRegistry;
import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.remoting.spi.Handle;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.CloseableTcpConnector;
import org.jboss.xnio.ConfigurableFactory;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.channels.Channels;
import org.jboss.xnio.channels.AllocatedMessageChannel;
import org.jboss.river.RiverMarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 *
 */
public final class MultiplexClientExample {

    static {
        final Logger l = Logger.getLogger("");
        l.getHandlers()[0].setLevel(Level.ALL);
        l.setLevel(Level.INFO);
    }

    private MultiplexClientExample() {
    }

    public static void main(String[] args) {
        try {
            final Endpoint endpoint = Remoting.createEndpoint("example-client-endpoint");
            try {
                // now create the client
                final NamedServiceRegistry serviceRegistry = new NamedServiceRegistry();
                final MultiplexConfiguration config = new MultiplexConfiguration();
                config.setNamedServiceRegistry(serviceRegistry);
                config.setAllocator(Buffers.createHeapByteBufferAllocator(1024));
                config.setMarshallerFactory(new RiverMarshallerFactory());
                config.setExecutor(IoUtils.directExecutor());
                config.setLinkMetric(100);
                config.setMarshallingConfiguration(new MarshallingConfiguration());
                final Xnio xnio = Xnio.create();
                try {
                    final ConfigurableFactory<CloseableTcpConnector> tcpConnectorFactory = xnio.createTcpConnector();
                    final CloseableTcpConnector closeableTcpConnector = tcpConnectorFactory.create();
                    try {
                        final ChannelSource<AllocatedMessageChannel> channelSource = Channels.convertStreamToAllocatedMessage(closeableTcpConnector.createChannelSource(new InetSocketAddress("localhost", 10000)), 1024, 1024);
                        final IoFuture<MultiplexConnection> futureConnection = MultiplexProtocol.connect(config, channelSource);
                        final MultiplexConnection connection = futureConnection.get();
                        try {
                            final Handle<RequestHandlerSource> handle = connection.openRemoteService(QualifiedName.parse("/jboss/example/string-rot-13"));
                            try {
                                final ClientSource<String, String> clientSource = endpoint.createClientSource(handle.getResource(), String.class, String.class);
                                try {
                                    final Client<String, String> client = clientSource.createClient();
                                    try {
                                        System.out.println("Enter text, send EOF to terminate");
                                        final BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));
                                        String line;
                                        while ((line = inputReader.readLine()) != null) {
                                            System.out.println("Response: " + client.invoke(line));
                                        }
                                        System.out.println("Done!");
                                    } finally {
                                        IoUtils.safeClose(client);
                                    }
                                } finally {
                                    IoUtils.safeClose(clientSource);
                                }
                            } finally {
                                IoUtils.safeClose(handle);
                            }
                        } finally {
                            IoUtils.safeClose(connection);
                        }
                    } finally {
                        IoUtils.safeClose(closeableTcpConnector);
                    }
                } finally {
                    IoUtils.safeClose(xnio);
                }
            } finally {
                IoUtils.safeClose(endpoint);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
