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

package org.jboss.remoting3.remote;

import java.nio.ByteBuffer;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.xnio.ChannelThreadPool;
import org.xnio.ConnectionChannelThread;
import org.xnio.Pool;
import org.xnio.ReadChannelThread;
import org.xnio.WriteChannelThread;
import org.xnio.Xnio;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemoteConnectionProviderFactory implements ConnectionProviderFactory {

    private final Xnio xnio;
    private final Pool<ByteBuffer> bufferPool;
    private final ChannelThreadPool<ReadChannelThread> readThreadPool;
    private final ChannelThreadPool<WriteChannelThread> writeThreadPool;
    private final ChannelThreadPool<ConnectionChannelThread> connectionThreadPool;

    /**
     * Construct a new instance.
     *
     * @param xnio the XNIO provider to use
     * @param bufferPool the buffer pool to use
     * @param readThreadPool the read thread pool to use
     * @param writeThreadPool the write thread pool to use
     * @param connectionThreadPool the connection thread pool to use
     */
    public RemoteConnectionProviderFactory(final Xnio xnio, final Pool<ByteBuffer> bufferPool, final ChannelThreadPool<ReadChannelThread> readThreadPool, final ChannelThreadPool<WriteChannelThread> writeThreadPool, final ChannelThreadPool<ConnectionChannelThread> connectionThreadPool) {
        this.xnio = xnio;
        this.bufferPool = bufferPool;
        this.readThreadPool = readThreadPool;
        this.writeThreadPool = writeThreadPool;
        this.connectionThreadPool = connectionThreadPool;
    }

    /** {@inheritDoc} */
    public ConnectionProvider createInstance(final ConnectionProviderContext context) {
        return new RemoteConnectionProvider(xnio, bufferPool, readThreadPool, writeThreadPool, connectionThreadPool, context);
    }
}
