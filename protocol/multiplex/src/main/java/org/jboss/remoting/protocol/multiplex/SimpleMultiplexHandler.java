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

import org.jboss.xnio.DelegatingIoHandler;
import org.jboss.xnio.AbstractIoFuture;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.AllocatedMessageChannel;
import org.jboss.remoting.Endpoint;

/**
 *
 */
public final class SimpleMultiplexHandler extends DelegatingIoHandler<AllocatedMessageChannel> {

    private volatile MultiplexConnection connection;
    private final Endpoint endpoint;
    private final MultiplexConfiguration configuration;
    private final FutureConnection futureConnection = new FutureConnection();

    public SimpleMultiplexHandler(final Endpoint endpoint, final MultiplexConfiguration configuration) {
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    public void handleOpened(final AllocatedMessageChannel channel) {
        connection = new MultiplexConnection(endpoint, channel, configuration);
        futureConnection.setResult(connection);
        setReadHandler(new MultiplexReadHandler(connection));
        channel.resumeReads();
    }

    public void handleClosed(final AllocatedMessageChannel channel) {
        IoUtils.safeClose(connection);
    }

    public IoFuture<MultiplexConnection> getConnection() {
        return futureConnection;
    }

    public static final class FutureConnection extends AbstractIoFuture<MultiplexConnection> {
        public IoFuture<MultiplexConnection> cancel() {
            return this;
        }

        protected boolean setResult(final MultiplexConnection result) {
            return super.setResult(result);
        }
    }
}
