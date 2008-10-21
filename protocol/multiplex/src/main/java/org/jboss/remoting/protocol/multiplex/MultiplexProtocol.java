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

import org.jboss.remoting.RemotingException;
import org.jboss.remoting.SimpleCloseable;
import org.jboss.remoting.Endpoint;
import org.jboss.remoting.spi.remote.RequestHandlerSource;
import org.jboss.remoting.spi.remote.Handle;
import org.jboss.remoting.spi.stream.StreamProvider;
import org.jboss.xnio.IoHandlerFactory;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.AbstractConvertingIoFuture;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.channels.AllocatedMessageChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

/**
 *
 */
public final class MultiplexProtocol {

    private static final Logger log = Logger.getLogger(MultiplexProtocol.class);

    private MultiplexProtocol() {
    }

    /**
     * Create a request server for the multiplex protocol.
     *
     * @param executor the executor to use for invocations
     * @param allocator the buffer allocator to use
     * @return a handler factory for passing to an XNIO server
     * @param <A> stream channel address type
     */
    public static <A> IoHandlerFactory<AllocatedMessageChannel> createServer(final Endpoint endpoint, final Executor executor, final BufferAllocator<ByteBuffer> allocator, final StreamProvider<A> streamProvider) {
        return new IoHandlerFactory<AllocatedMessageChannel>() {
            public IoHandler<? super AllocatedMessageChannel> createHandler() {
                final RemotingChannelConfiguration configuration = new RemotingChannelConfiguration();
                configuration.setAllocator(allocator);
                configuration.setExecutor(executor);
                // todo marshaller factory... etc
                return new MultiplexHandler<A>(endpoint, configuration, streamProvider);
            }
        };
    }

    /**
     * Create a request client for the multiplex protocol.
     *
     * @return a handle which may be used to close the connection
     * @throws IOException if an error occurs @param executor the executor to use for invocations
     * @param channelSource the XNIO channel source to use to establish the connection
     * @param allocator the buffer allocator to use
     * @param streamProvider
     */
    public static <A> IoFuture<SimpleCloseable> connect(final Endpoint endpoint, final Executor executor, final ChannelSource<AllocatedMessageChannel> channelSource, final BufferAllocator<ByteBuffer> allocator, final StreamProvider<A> streamProvider) throws IOException {
        final RemotingChannelConfiguration configuration = new RemotingChannelConfiguration();
        configuration.setAllocator(allocator);
        configuration.setExecutor(executor);
        // todo marshaller factory... etc
        final MultiplexHandler multiplexHandler = new MultiplexHandler<A>(endpoint, configuration, streamProvider);
        final IoFuture<AllocatedMessageChannel> futureChannel = channelSource.open(multiplexHandler);
        return new AbstractConvertingIoFuture<SimpleCloseable, AllocatedMessageChannel>(futureChannel) {
            protected SimpleCloseable convert(final AllocatedMessageChannel channel) throws RemotingException {
                return new AbstractConnection(executor) {
                    public Handle<RequestHandlerSource> getServiceForId(final int id) throws IOException {
                        return multiplexHandler.getRemoteService(id).getHandle();
                    }
                };
            }
        };
    }
}
