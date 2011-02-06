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

import java.io.IOException;
import java.nio.ByteBuffer;
import org.jboss.marshalling.NioByteInput;
import org.xnio.Pool;
import org.jboss.logging.Logger;

final class InboundRequestInputHandler implements NioByteInput.InputHandler {
    private final int rid;
    private final InboundRequest inboundRequest;

    private static final Logger log = Loggers.main;

    public InboundRequestInputHandler(final InboundRequest inboundRequest, final int rid) {
        this.inboundRequest = inboundRequest;
        this.rid = rid;
    }

    public void acknowledge() throws IOException {
        final RemoteConnectionHandler connectionHandler = inboundRequest.getRemoteConnectionHandler();
        final Pool<ByteBuffer> bufferPool = connectionHandler.getBufferPool();
        final ByteBuffer buffer = bufferPool.allocate();
        try {
            buffer.putInt(RemoteConnectionHandler.LENGTH_PLACEHOLDER);
            buffer.put(RemoteProtocol.REQUEST_ACK_CHUNK);
            buffer.putInt(rid);
            buffer.flip();
            final RemoteConnection connection = connectionHandler.getRemoteConnection();
            connection.sendBlocking(buffer, true);
        } catch (IOException e) {
            log.trace(e, "Failed to acknowledge chunk for %s", this);
        } finally {
            bufferPool.free(buffer);
        }
    }

    public void close() throws IOException {
        // no operation
    }

    public String toString() {
        return "Inbound request input handler for request ID " + rid;
    }
}
