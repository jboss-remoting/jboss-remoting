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

package org.jboss.remoting3.samples.simple;

import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.LocalServiceConfiguration;
import org.jboss.remoting3.SimpleCloseable;
import org.jboss.remoting3.spi.ConnectionProviderRegistration;
import org.jboss.remoting3.multiplex.MultiplexConnectionProviderFactory;
import org.jboss.remoting3.multiplex.MultiplexServerFactory;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.CloseableExecutor;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.TcpServer;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.channels.Channels;
import java.io.IOException;

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

    public static void main(String[] args) throws InterruptedException, IOException {
        final CloseableExecutor executor = Remoting.createExecutor(10);
        try {
            Xnio xnio = Xnio.create();
            try {
                final Endpoint endpoint = Remoting.createEndpoint(executor, "example-endpoint");
                try {
                    final LocalServiceConfiguration<String, String> config = LocalServiceConfiguration.create(new StringRot13ClientListener(), String.class, String.class);
                    config.setGroupName("main");
                    config.setServiceType("simple.rot13");
                    final SimpleCloseable handle = endpoint.registerService(config);
                    try {
                        // now create the server...
                        final MultiplexConnectionProviderFactory multiplexConnectionProviderFactory = new MultiplexConnectionProviderFactory(xnio.createTcpConnector(OptionMap.EMPTY));
                        final ConnectionProviderRegistration<MultiplexServerFactory> cpHandle = endpoint.addConnectionProvider("multiplex", multiplexConnectionProviderFactory);
                        try {
                            final TcpServer tcpServer = xnio.createTcpServer(Channels.createAllocatedMessageChannel(cpHandle.getProviderInterface().getServerListener(), OptionMap.EMPTY)).create();
                            try {
                                // now just wait for 15 seconds, and then shut it all down
                                Thread.sleep(15000L);
                            } finally {
                                IoUtils.safeClose(tcpServer);
                            }
                        } finally {
                            IoUtils.safeClose(cpHandle);
                        }
                    } finally {
                        IoUtils.safeClose(handle);
                    }
                } finally {
                    IoUtils.safeClose(endpoint);
                }
            } finally {
                IoUtils.safeClose(xnio);
            }
        } finally {
            IoUtils.safeClose(executor);
        }
    }

}
