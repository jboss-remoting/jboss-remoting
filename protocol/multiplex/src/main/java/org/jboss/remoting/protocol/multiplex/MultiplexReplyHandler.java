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

import org.jboss.remoting.spi.ReplyHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.ByteOutput;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 *
 */
final class MultiplexReplyHandler implements ReplyHandler {

    private final int requestId;
    private final MultiplexConnection connection;

    MultiplexReplyHandler(final int requestId, final MultiplexConnection connection) {
        this.requestId = requestId;
        this.connection = connection;
    }

    public void handleReply(final Object reply) throws IOException {
        final MultiplexConnection connection = this.connection;
        final Marshaller marshaller = connection.getMarshallerFactory().createMarshaller(connection.getMarshallingConfiguration());
        try {
            final List<ByteBuffer> bufferList = new ArrayList<ByteBuffer>();
            final ByteOutput output = new BufferByteOutput(connection.getAllocator(), bufferList);
            try {
                marshaller.start(output);
                marshaller.write(MessageType.REPLY.getId());
                marshaller.writeInt(requestId);
                marshaller.writeObject(reply);
                marshaller.close();
                output.close();
                connection.doBlockingWrite(bufferList);
            } finally {
                IoUtils.safeClose(output);
            }
        } finally {
            IoUtils.safeClose(marshaller);
        }
    }

    public void handleException(final IOException exception) throws IOException {
        final MultiplexConnection connection = this.connection;
        final Marshaller marshaller = connection.getMarshallerFactory().createMarshaller(connection.getMarshallingConfiguration());
        try {
            final List<ByteBuffer> bufferList = new ArrayList<ByteBuffer>();
            final ByteOutput output = new BufferByteOutput(connection.getAllocator(), bufferList);
            try {
                marshaller.start(output);
                marshaller.write(MessageType.REQUEST_FAILED.getId());
                marshaller.writeInt(requestId);
                marshaller.writeObject(exception);
                marshaller.close();
                output.close();
                connection.doBlockingWrite(bufferList);
            } finally {
                IoUtils.safeClose(output);
            }
        } finally {
            IoUtils.safeClose(marshaller);
        }
    }

    public void handleCancellation() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(5);
        buffer.put((byte) MessageType.CANCEL_ACK.getId());
        buffer.putInt(requestId);
        buffer.flip();
        connection.doBlockingWrite(buffer);
    }
}
