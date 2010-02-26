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
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.SpiUtils;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.Pool;

final class OutboundRequestHandler extends AbstractHandleableCloseable<RequestHandler> implements RequestHandler {

    private final OutboundClient outboundClient;

    OutboundRequestHandler(final OutboundClient outboundClient) {
        super(outboundClient.getRemoteConnectionHandler().getConnectionContext().getConnectionProviderContext().getExecutor());
        this.outboundClient = outboundClient;
    }

    public Cancellable receiveRequest(final Object request, final ReplyHandler replyHandler) {
        final RemoteConnectionHandler connectionHandler = outboundClient.getRemoteConnectionHandler();
        final OutboundRequest outboundRequest = new OutboundRequest(connectionHandler, replyHandler, outboundClient.getId());
        int rid;
        final IntKeyMap<OutboundRequest> outboundRequests = connectionHandler.getOutboundRequests();
        final Random random = connectionHandler.getRandom();
        synchronized (outboundRequests) {
            while (outboundRequests.containsKey(rid = random.nextInt()));
            outboundRequests.put(rid, outboundRequest);
        }
        final NioByteOutput byteOutput = new NioByteOutput(new RequestBufferWriter(outboundRequest, rid));
        try {
            RemoteConnectionHandler.log.trace("Starting sending request %s", request);
            final Marshaller marshaller = connectionHandler.getMarshallerFactory().createMarshaller(connectionHandler.getMarshallingConfiguration());
            marshaller.start(byteOutput);
            marshaller.writeObject(request);
            marshaller.finish();
            RemoteConnectionHandler.log.trace("Finished sending request %s", request);
        } catch (IOException e) {
            RemoteConnectionHandler.log.trace(e, "Got exception while marshalling request %s", request);
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
                connectionHandler.sendBlocking(buf);
            } catch (IOException e1) {
                // todo log it
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
            buf.put(RemoteProtocol.CLIENT_CLOSED);
            buf.putInt(outboundClient.getId());
            buf.flip();
            connectionHandler.sendBlocking(buf);
        } finally {
            bufferPool.free(buf);
        }
    }

    public Key addCloseHandler(final CloseHandler<? super RequestHandler> closeHandler) {
        return null;
    }
}
