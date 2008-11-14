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

import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.AbstractAutoCloseable;
import org.jboss.remoting.spi.RemoteRequestContext;
import org.jboss.remoting.spi.ReplyHandler;
import org.jboss.remoting.spi.SpiUtils;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.ByteOutput;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;

/**
 *
 */
final class MultiplexRequestHandler extends AbstractAutoCloseable<RequestHandler> implements RequestHandler {
    private static final Logger log = Logger.getLogger("org.jboss.remoting.multiplex.request-handler");

    private final int identifier;
    private final BufferAllocator<ByteBuffer> allocator;
    private final MultiplexConnection connection;

    public MultiplexRequestHandler(final int identifier, final MultiplexConnection connection) {
        super(connection.getExecutor());
        this.connection = connection;
        this.identifier = identifier;
        allocator = connection.getAllocator();
    }

    @Override
    protected void closeAction() throws IOException {
        connection.removeRemoteClient(identifier);
        ByteBuffer buffer = allocator.allocate();
        buffer.put((byte) MessageType.CLIENT_CLOSE.getId());
        buffer.putInt(identifier);
        buffer.flip();
        connection.doBlockingWrite(buffer);
    }

    public RemoteRequestContext receiveRequest(final Object request, final ReplyHandler handler) {
        log.trace("Sending outbound request of type %s", request == null ? "null" : request.getClass());
        final List<ByteBuffer> bufferList;
        final MultiplexConnection connection = this.connection;
        try {
            final Marshaller marshaller = connection.getMarshallerFactory().createMarshaller(connection.getMarshallingConfiguration());
            try {
                bufferList = new ArrayList<ByteBuffer>();
                final ByteOutput output = new BufferByteOutput(allocator, bufferList);
                try {
                    marshaller.start(output);
                    marshaller.write(MessageType.REQUEST.getId());
                    marshaller.writeInt(identifier);
                    final int id = connection.nextRequest();
                    connection.addRemoteRequest(id, handler);
                    marshaller.writeInt(id);
                    marshaller.writeObject(request);
                    marshaller.close();
                    output.close();
                    connection.doBlockingWrite(bufferList);
                    log.trace("Sent request %s", request);
                    return new RemoteRequestContextImpl(id, connection);
                } finally {
                    IoUtils.safeClose(output);
                }
            } finally {
                IoUtils.safeClose(marshaller);
            }
        } catch (final IOException t) {
            log.trace(t, "receiveRequest failed with an exception");
            SpiUtils.safeHandleException(handler, t);
            return SpiUtils.getBlankRemoteRequestContext();
        }
    }

    public String toString() {
        return "forwarding request handler <" + Integer.toString(hashCode(), 16) + "> (id = " + identifier + ")";
    }
}

final class RemoteRequestContextImpl implements RemoteRequestContext {

    private static final Logger log = Logger.getLogger("org.jboss.remoting.multiplex.requesthandler.context");

    private final int id;
    private final MultiplexConnection connection;
    private final AtomicBoolean cancelSent = new AtomicBoolean();

    public RemoteRequestContextImpl(final int id, final MultiplexConnection connection) {
        this.id = id;
        this.connection = connection;
    }

    public void cancel() {
        if (! cancelSent.getAndSet(true)) try {
            final ByteBuffer buffer = ByteBuffer.allocate(5);
            buffer.put((byte) MessageType.CANCEL_REQUEST.getId());
            buffer.putInt(id);
            buffer.flip();
            connection.doBlockingWrite(buffer);
        } catch (Throwable t) {
            log.warn("Sending cancel request failed: %s", t);
        }
    }

    public String toString() {
        return "remote request context (multiplex) <" + Integer.toString(hashCode(), 16) + "> (id = " + id + ")";
    }
}
