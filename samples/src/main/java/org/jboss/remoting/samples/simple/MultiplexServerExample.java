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

import org.jboss.remoting.Remoting;
import org.jboss.remoting.Endpoint;
import org.jboss.remoting.LocalServiceConfiguration;
import org.jboss.remoting.protocol.multiplex.MultiplexProtocol;
import org.jboss.remoting.protocol.multiplex.MultiplexConfiguration;
import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.remoting.spi.Handle;
import org.jboss.remoting.spi.NamedServiceRegistry;
import org.jboss.remoting.QualifiedName;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.ConfigurableFactory;
import org.jboss.xnio.CloseableExecutor;
import org.jboss.xnio.channels.AllocatedMessageChannel;
import org.jboss.xnio.channels.Channels;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.river.RiverMarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.reflect.SunReflectiveCreator;
import java.io.IOException;
import java.io.Closeable;
import java.net.InetSocketAddress;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 *
 */
public final class MultiplexServerExample {

    static {
        final Logger l = Logger.getLogger("");
        l.getHandlers()[0].setLevel(Level.ALL);
        l.setLevel(Level.INFO);
    }

    private MultiplexServerExample() {
    }

    public static void main(String[] args) {
        final CloseableExecutor executor = Remoting.createExecutor(10);
        try {
            final Endpoint endpoint = Remoting.createEndpoint(executor, "example-endpoint");
            try {
                final StringRot13RequestListener listener = new StringRot13RequestListener();
                final LocalServiceConfiguration<String, String> config = new LocalServiceConfiguration<String, String>(listener, String.class, String.class);
                config.setGroupName("main");
                config.setServiceType("jboss.example.streaming-rot-13");
                final Handle<RequestHandlerSource> handle = endpoint.registerService(config);
                try {
                    // now create the server...
                    final NamedServiceRegistry serviceRegistry = new NamedServiceRegistry();
                    final Handle<RequestHandlerSource> connHandle = serviceRegistry.registerService(QualifiedName.parse("/jboss/example/string-rot-13"), handle.getResource());
                    try {
                        final MultiplexConfiguration multiplexConfig = new MultiplexConfiguration();
                        multiplexConfig.setNamedServiceRegistry(serviceRegistry);
                        multiplexConfig.setAllocator(Buffers.createHeapByteBufferAllocator(1024));
                        multiplexConfig.setLinkMetric(100);
                        multiplexConfig.setMarshallerFactory(new RiverMarshallerFactory());
                        multiplexConfig.setExecutor(executor);
                        final MarshallingConfiguration marshallingConfig = new MarshallingConfiguration();
                        marshallingConfig.setCreator(new SunReflectiveCreator());
                        multiplexConfig.setMarshallingConfiguration(marshallingConfig);
                        final IoHandlerFactory<AllocatedMessageChannel> handlerFactory = MultiplexProtocol.createServer(endpoint, multiplexConfig);
                        final IoHandlerFactory<StreamChannel> streamHandlerFactory = Channels.convertStreamToAllocatedMessage(handlerFactory, 1024, 1024);
                        // finally, bind it
                        final Xnio xnio = Xnio.create();
                        try {
                            final ConfigurableFactory<? extends Closeable> tcpServerFactory = xnio.createTcpServer(streamHandlerFactory, new InetSocketAddress(10000));
                            final Closeable server = tcpServerFactory.create();
                            try {
                                System.out.println("Press enter to terminate.");
                                while (System.in.read() != '\n');
                            } finally {
                                IoUtils.safeClose(server);
                            }
                        } finally {
                            IoUtils.safeClose(xnio);
                        }
                    } finally {
                        IoUtils.safeClose(connHandle);
                    }
                } finally {
                    IoUtils.safeClose(handle);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                IoUtils.safeClose(endpoint);
            }
        } finally {
            IoUtils.safeClose(executor);
        }
    }

}
