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

import org.jboss.cx.remoting.spi.remote.RemoteClientEndpoint;
import org.jboss.cx.remoting.spi.remote.RemoteServiceEndpoint;
import org.jboss.cx.remoting.spi.remote.RemoteClientEndpointListener;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.core.marshal.JavaSerializationMarshallerFactory;
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
public final class BasicProtocol {

    private static final Logger log = Logger.getLogger(BasicProtocol.class);

    private BasicProtocol() {
    }

    /**
     * Create a request server for the basic protocol.
     *
     * @param executor the executor to use for invocations
     * @param localRootSource the service to draw client endpoints from for root clients on inbound connections
     * @param allocator the buffer allocator to use
     * @param remoteListener a listener which receives notification of the remote root client of the incoming connection
     * @return a handler factory for passing to an XNIO server
     */
    public static IoHandlerFactory<AllocatedMessageChannel> createServer(final Executor executor, final RemoteServiceEndpoint localRootSource, final BufferAllocator<ByteBuffer> allocator, final RemoteClientEndpointListener remoteListener) {
        return new IoHandlerFactory<AllocatedMessageChannel>() {
            public IoHandler<? super AllocatedMessageChannel> createHandler() {
                try {
                    final RemoteClientEndpoint remoteClientEndpoint = localRootSource.createClientEndpoint();
                    try {
                        return new BasicHandler(true, allocator, remoteClientEndpoint, executor, remoteListener, new JavaSerializationMarshallerFactory(executor));
                    } finally {
                        try {
                            remoteClientEndpoint.autoClose();
                        } catch (RemotingException e) {
                            log.error(e, "Error setting auto-close mode");
                        }
                    }
                } catch (RemotingException e) {
                    throw new IllegalStateException("The local root endpoint is unusable", e);
                }
            }
        };
    }

    /**
     * Create a request client for the basic protocol.
     *
     * @param <I> the request type of the new remote root service endpoint
     * @param <O> the reply type of the new remote root service endpoint
     * @param executor the executor to use for invocations
     * @param localRoot the client endpoint to use as the local root client
     * @param channelSource the XNIO channel source to use to establish the connection
     * @param allocator the buffer allocator to use
     * @return the future client endpoint of the remote side's root client
     * @throws IOException if an error occurs
     */
    public static <I, O> IoFuture<RemoteClientEndpoint> connect(final Executor executor, final RemoteClientEndpoint localRoot, final ChannelSource<AllocatedMessageChannel> channelSource, final BufferAllocator<ByteBuffer> allocator) throws IOException {
        final BasicHandler basicHandler = new BasicHandler(false, allocator, localRoot, executor, null, new JavaSerializationMarshallerFactory(executor));
        final IoFuture<AllocatedMessageChannel> futureChannel = channelSource.open(basicHandler);
        return new AbstractConvertingIoFuture<RemoteClientEndpoint, AllocatedMessageChannel>(futureChannel) {
            @SuppressWarnings({ "unchecked" })
            protected RemoteClientEndpoint convert(final AllocatedMessageChannel channel) throws RemotingException {
                final RemoteClientEndpoint remoteClientEndpoint = basicHandler.getRemoteClient(0);
                return (RemoteClientEndpoint) remoteClientEndpoint;
            }
        };
    }
}
