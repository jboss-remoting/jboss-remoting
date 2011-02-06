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
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.NioByteOutput;
import org.xnio.Pool;

final class OutboundReplyHandler implements RemoteReplyHandler {

    private final int rid;
    private final AtomicBoolean done = new AtomicBoolean();
    private InboundRequest inboundRequest;

    public OutboundReplyHandler(final InboundRequest inboundRequest, final int rid) {
        this.inboundRequest = inboundRequest;
        this.rid = rid;
    }

    public void handleReply(final Object reply) throws IOException {
        if (! done.getAndSet(true)) {
            final RemoteConnectionHandler connectionHandler = inboundRequest.getRemoteConnectionHandler();
            final Marshaller marshaller = connectionHandler.getMarshallerFactory().createMarshaller(connectionHandler.getMarshallingConfiguration());
            marshaller.start(new NioByteOutput(new OutboundReplyBufferWriter(inboundRequest, rid, false)));
            final RemoteConnectionHandler old = RemoteConnectionHandler.setCurrent(connectionHandler);
            try {
                marshaller.writeObject(reply);
                marshaller.finish();
            } finally {
                RemoteConnectionHandler.setCurrent(old);
            }
        }
    }

    public void handleException(final IOException exception) throws IOException {
        if (! done.getAndSet(true)) {
            final RemoteConnectionHandler connectionHandler = inboundRequest.getRemoteConnectionHandler();
            boolean ok = false;
            try {
                final Marshaller marshaller = connectionHandler.getMarshallerFactory().createMarshaller(connectionHandler.getMarshallingConfiguration());
                marshaller.start(new NioByteOutput(new OutboundReplyBufferWriter(inboundRequest, rid, true)));
                final RemoteConnectionHandler old = RemoteConnectionHandler.setCurrent(connectionHandler);
                try {
                    marshaller.writeObject(exception);
                    marshaller.finish();
                } finally {
                    RemoteConnectionHandler.setCurrent(old);
                }
                ok = true;
            } finally {
                if (! ok) {
                    // attempt to send an exception abort
                    final Pool<ByteBuffer> bufferPool = connectionHandler.getBufferPool();
                    final ByteBuffer buffer = bufferPool.allocate();
                    try {
                        buffer.putInt(RemoteConnectionHandler.LENGTH_PLACEHOLDER);
                        buffer.put(RemoteProtocol.REPLY_EXCEPTION_ABORT);
                        buffer.putInt(rid);
                        buffer.flip();
                        connectionHandler.getRemoteConnection().sendBlocking(buffer, true);
                    } finally {
                        bufferPool.free(buffer);
                    }
                }
            }
        }
    }

    public void handleCancellation() throws IOException {
        setDone();
    }

    public void setDone() {
        done.set(true);
    }
}
