/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.remote;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.jboss.remoting3.spi.LocalRequestHandler;
import org.jboss.remoting3.spi.SpiUtils;

final class InboundClient implements Closeable {
    private final LocalRequestHandler handler;
    private final RemoteConnectionHandler remoteConnectionHandler;
    private final int id;

    InboundClient(final RemoteConnectionHandler remoteConnectionHandler, final LocalRequestHandler handler, final int id) {
        this.remoteConnectionHandler = remoteConnectionHandler;
        this.handler = handler;
        this.id = id;
        handler.addCloseHandler(SpiUtils.closingCloseHandler(this));
    }

    LocalRequestHandler getHandler() {
        return handler;
    }

    RemoteConnectionHandler getRemoteConnectionHandler() {
        return remoteConnectionHandler;
    }

    public void close() {
        final RemoteConnection remoteConnection = remoteConnectionHandler.getRemoteConnection();
        final ByteBuffer buffer = remoteConnection.allocate();
        try {
            buffer.position(4);
            buffer.put(RemoteProtocol.CLIENT_ASYNC_CLOSE);
            buffer.putInt(id);
            buffer.flip();
            try {
                remoteConnection.sendBlocking(buffer, true);
            } catch (IOException e) {
                // irrelevant
            }
        } finally {
            remoteConnection.free(buffer);
        }
    }
}
