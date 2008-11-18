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

import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.remoting.spi.AbstractAutoCloseable;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.Handle;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;
import java.nio.ByteBuffer;
import java.io.IOException;

/**
 *
 */
final class MultiplexRequestHandlerSource extends AbstractAutoCloseable<RequestHandlerSource> implements RequestHandlerSource {

    private static final Logger log = Logger.getLogger("org.jboss.remoting.multiplex.request-handler-source");

    private final int identifier;
    private final MultiplexConnection connection;

    MultiplexRequestHandlerSource(final int identifier, final MultiplexConnection connection) {
        super(connection.getExecutor());
        this.connection = connection;
        this.identifier = identifier;
    }

    @Override
    protected void closeAction() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put((byte) MessageType.SERVICE_CLOSE_REQUEST.getId());
        buffer.putInt(identifier);
        buffer.flip();
        connection.doBlockingWrite(buffer);
    }

    public Handle<RequestHandler> createRequestHandler() throws IOException {
        log.trace("Creating new request handler from %s", this);
        final int id = connection.nextRemoteClient();
        final RequestHandler requestHandler = new MultiplexRequestHandler(id, connection);
        boolean ok = false;
        try {
            connection.addRemoteClient(id, requestHandler);
            try {
                final ByteBuffer buffer = ByteBuffer.allocate(9);
                buffer.put((byte) MessageType.CLIENT_OPEN.getId());
                buffer.putInt(identifier);
                buffer.putInt(id);
                buffer.flip();
                connection.doBlockingWrite(buffer);
                final Handle<RequestHandler> handlerHandle = new MultiplexRequestHandler(id, connection).getHandle();
                log.trace("Created new request handler with a handle of %s", handlerHandle);
                ok = true;
                return handlerHandle;
            } finally {
                if (! ok) {
                    connection.removeRemoteClient(id);
                }
            }
        } finally {
            if (! ok) {
                IoUtils.safeClose(requestHandler);
            }
        }
    }

    public String toString() {
        return "forwarding request handler source <" + Integer.toString(hashCode(), 16) + "> (id = " + identifier + ") for " + connection;
    }
}
