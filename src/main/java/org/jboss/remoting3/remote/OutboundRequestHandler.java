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
import java.util.Random;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.NioByteOutput;
import org.jboss.marshalling.util.IntKeyMap;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.SpiUtils;
import org.xnio.Cancellable;
import org.xnio.Pool;
import org.jboss.logging.Logger;

final class OutboundRequestHandler extends AbstractHandleableCloseable<RemoteRequestHandler> implements RemoteRequestHandler {

    private final OutboundClient outboundClient;
    private static final Logger log = Loggers.main;

    OutboundRequestHandler(final OutboundClient outboundClient) {
        super(outboundClient.getRemoteConnectionHandler().getConnectionContext().getConnectionProviderContext().getExecutor());
        this.outboundClient = outboundClient;
    }

    public Cancellable receiveRequest(final Object request, final LocalReplyHandler replyHandler) {
        final RemoteConnectionHandler connectionHandler = outboundClient.getRemoteConnectionHandler();
        final OutboundRequest outboundRequest = new OutboundRequest(connectionHandler, replyHandler, outboundClient.getId());
        int rid;
        final IntKeyMap<OutboundRequest> outboundRequests = connectionHandler.getOutboundRequests();
        final Random random = connectionHandler.getRandom();
        synchronized (outboundRequests) {
            while (outboundRequests.containsKey(rid = random.nextInt()));
            outboundRequests.put(rid, outboundRequest);
        }
        final NioByteOutput byteOutput = new NioByteOutput(new OutboundRequestBufferWriter(outboundRequest, rid));
        try {
            log.trace("Starting sending request %s for %s", request, Integer.valueOf(rid));
            final Marshaller marshaller = connectionHandler.getMarshallerFactory().createMarshaller(connectionHandler.getMarshallingConfiguration());
            marshaller.start(byteOutput);
            RemoteConnectionHandler old = RemoteConnectionHandler.setCurrent(connectionHandler);
            try {
                marshaller.writeObject(request);
                marshaller.finish();
            } finally {
                RemoteConnectionHandler.setCurrent(old);
            }
            log.trace("Finished sending request %s", request);
        } catch (IOException e) {
            log.trace(e, "Got exception while marshalling request %s", request);
            SpiUtils.safeHandleException(replyHandler, e);
            synchronized (outboundRequests) {
                outboundRequests.remove(rid);
            }
            synchronized (outboundRequest) {
                outboundRequest.setState(OutboundRequest.State.CLOSED);
            }
            // send request abort msg
            final ByteBuffer buf = connectionHandler.getBufferPool().allocate();
            buf.putInt(RemoteConnectionHandler.LENGTH_PLACEHOLDER);
            buf.put(RemoteProtocol.REQUEST_ABORT);
            buf.putInt(rid);
            buf.flip();
            try {
                connectionHandler.getRemoteConnection().sendBlocking(buf, true);
            } catch (IOException e1) {
                log.trace("Send failed: %s", e1);
            }
        }
        return outboundRequest;
    }

    public void close() throws IOException {
        synchronized (outboundClient) {
            if (outboundClient.getState() == OutboundClient.State.CLOSED) return;
            outboundClient.setState(OutboundClient.State.CLOSED);
        }
        final RemoteConnectionHandler connectionHandler = outboundClient.getRemoteConnectionHandler();
        final Pool<ByteBuffer> bufferPool = connectionHandler.getBufferPool();
        final ByteBuffer buf = bufferPool.allocate();
        try {
            buf.putInt(RemoteConnectionHandler.LENGTH_PLACEHOLDER);
            buf.put(RemoteProtocol.CHANNEL_CLOSE);
            buf.putInt(outboundClient.getId());
            buf.flip();
            connectionHandler.getRemoteConnection().sendBlocking(buf, true);
        } finally {
            bufferPool.free(buf);
        }
    }
}
