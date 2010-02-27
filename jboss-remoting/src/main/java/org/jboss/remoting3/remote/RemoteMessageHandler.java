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
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import org.jboss.marshalling.NioByteInput;
import org.jboss.marshalling.util.IntKeyMap;
import org.jboss.remoting3.ReplyException;
import org.jboss.remoting3.ServiceNotFoundException;
import org.jboss.remoting3.ServiceURI;
import org.jboss.remoting3.spi.ReplyHandler;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.SpiUtils;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Pool;

final class RemoteMessageHandler implements org.jboss.xnio.channels.MessageHandler {

    private RemoteConnectionHandler remoteConnectionHandler;

    public RemoteMessageHandler(final RemoteConnectionHandler remoteConnectionHandler) {
        this.remoteConnectionHandler = remoteConnectionHandler;
    }

    public void handleMessage(final ByteBuffer buffer) {
        final byte cmd = buffer.get();
        final RemoteConnectionHandler connectionHandler = remoteConnectionHandler;
        switch (cmd) {
            case RemoteProtocol.SERVICE_REQUEST: {
                final int id = buffer.getInt();
                final String serviceType = Buffers.getModifiedUtf8Z(buffer);
                final String groupName = Buffers.getModifiedUtf8Z(buffer);
                final RequestHandler handler;
                handler = connectionHandler.getConnectionContext().openService(serviceType, groupName, OptionMap.EMPTY);
                final Pool<ByteBuffer> bufferPool = connectionHandler.getBufferPool();
                final ByteBuffer outBuf = bufferPool.allocate();
                try {
                    outBuf.putInt(RemoteConnectionHandler.LENGTH_PLACEHOLDER);
                    if (handler == null) {
                        // no matching service found
                        outBuf.put(RemoteProtocol.SERVICE_NOT_FOUND);
                    } else {
                        // service opened locally, now register the success
                        final InboundClient inboundClient = new InboundClient(connectionHandler, handler);
                        final IntKeyMap<InboundClient> inboundClients = connectionHandler.getInboundClients();
                        synchronized (inboundClients) {
                            inboundClients.put(id, inboundClient);
                        }
                        outBuf.put(RemoteProtocol.SERVICE_CLIENT_OPENED);
                    }
                    outBuf.putInt(id);
                    outBuf.flip();
                    try {
                        connectionHandler.sendBlocking(outBuf);
                    } catch (IOException e) {
                        // the channel has suddenly failed
                        RemoteConnectionHandler.log.trace("Send failed: %s", e);
                    }
                    return;
                } finally {
                    bufferPool.free(outBuf);
                }
                // not reached
            }
            case RemoteProtocol.SERVICE_NOT_FOUND: {
                final int id = buffer.getInt();
                final OutboundClient client;
                final IntKeyMap<OutboundClient> outboundClients = connectionHandler.getOutboundClients();
                synchronized (outboundClients) {
                    client = outboundClients.remove(id);
                }
                if (client == null) {
                    RemoteConnectionHandler.log.trace("Received service-not-found for unknown client %d", Integer.valueOf(id));
                    return;
                }
                synchronized (client) {
                    // todo assert client state == waiting
                    client.getResult().setException(new ServiceNotFoundException(ServiceURI.create(client.getServiceType(), client.getGroupName(), null)));
                    client.setState(OutboundClient.State.CLOSED);
                }
                return;
            }
            case RemoteProtocol.SERVICE_CLIENT_OPENED: {
                final int id = buffer.getInt();
                final OutboundClient client;
                final IntKeyMap<OutboundClient> outboundClients = connectionHandler.getOutboundClients();
                synchronized (outboundClients) {
                    client = outboundClients.get(id);
                }
                if (client == null) {
                    RemoteConnectionHandler.log.trace("Received service-client-opened for unknown client %d", Integer.valueOf(id));
                    return;
                }
                synchronized (client) {
                    // todo assert client state == waiting
                    client.setState(OutboundClient.State.ESTABLISHED);
                    client.getResult().setResult(new OutboundRequestHandler(client));
                }
                return;
            }
            case RemoteProtocol.CLIENT_CLOSED: {
                final int id = buffer.getInt();

                final InboundClient client;
                final IntKeyMap<InboundClient> inboundClients = connectionHandler.getInboundClients();
                synchronized (inboundClients) {
                    client = inboundClients.remove(id);
                }
                if (client == null) {
                    RemoteConnectionHandler.log.trace("Received client-closed for unknown client %d", Integer.valueOf(id));
                    return;
                }
                synchronized (client) {
                    IoUtils.safeClose(client.getHandler());
                }
                return;
            }
            case RemoteProtocol.REQUEST: {
                final int rid = buffer.getInt();
                final byte flags = buffer.get();
                final InboundRequest inboundRequest;
                final NioByteInput byteInput;
                final IntKeyMap<InboundRequest> inboundRequests = connectionHandler.getInboundRequests();
                final int cid;
                boolean start = false;
                synchronized (inboundRequests) {
                    if ((flags & RemoteProtocol.MSG_FLAG_FIRST) != 0) {
                        cid = buffer.getInt();
                        inboundRequest = new InboundRequest(connectionHandler, 0);
                        start = true;
                        // todo - check for duplicate
                        inboundRequests.put(rid, inboundRequest);
                    } else {
                        cid = 0;
                        inboundRequest = inboundRequests.get(rid);
                    }
                    if (inboundRequest == null) {
                        RemoteConnectionHandler.log.trace("Received request for unknown request ID %d", Integer.valueOf(rid));
                    }
                }
                synchronized (inboundRequest) {
                    if (start) {
                        connectionHandler.getConnectionContext().getConnectionProviderContext().getExecutor().execute(new InboundRequestTask(connectionHandler, inboundRequest, rid, cid));
                    }
                    byteInput = inboundRequest.getByteInput();
                }
                byteInput.push(buffer);
                return;
            }
            case RemoteProtocol.REQUEST_ABORT: {
                final int rid = buffer.getInt();
                final InboundRequest inboundRequest;
                final IntKeyMap<InboundRequest> inboundRequests = connectionHandler.getInboundRequests();
                synchronized (inboundRequests) {
                    inboundRequest = inboundRequests.remove(rid);
                }
                if (inboundRequest == null) {
                    RemoteConnectionHandler.log.trace("Received request-abort for unknown request ID %d", Integer.valueOf(rid));
                    return;
                }
                synchronized (inboundRequest) {
                    // as long as the last message hasn't been received yet, this will disrupt the request and prevent a reply
                    inboundRequest.getReplyHandler().setDone();
                    inboundRequest.getByteInput().pushException(new InterruptedIOException("Request aborted"));
                }
                return;
            }
            case RemoteProtocol.REQUEST_ACK_CHUNK: {
                final int rid = buffer.getInt();
                final InboundRequest inboundRequest;
                final IntKeyMap<InboundRequest> inboundRequests = connectionHandler.getInboundRequests();
                synchronized (inboundRequests) {
                    inboundRequest = inboundRequests.get(rid);
                }
                if (inboundRequest == null) {
                    RemoteConnectionHandler.log.trace("Received request-ack-chunk for unknown request ID %d", Integer.valueOf(rid));
                    return;
                }
                synchronized (inboundRequest) {
                    inboundRequest.ack();
                }
                return;
            }
            case RemoteProtocol.REPLY: {
                final int rid = buffer.getInt();
                final byte flags = buffer.get();
                final OutboundRequest outboundRequest;
                final NioByteInput byteInput;
                final IntKeyMap<OutboundRequest> outboundRequests = connectionHandler.getOutboundRequests();
                synchronized (outboundRequests) {
                    outboundRequest = outboundRequests.get(rid);
                }
                if (outboundRequest == null) {
                    RemoteConnectionHandler.log.trace("Received reply for unknown request ID %d", Integer.valueOf(rid));
                    return;
                }
                synchronized (outboundRequest) {
                    if ((flags & RemoteProtocol.MSG_FLAG_FIRST) != 0) {
                        // todo - check for duplicate
                        outboundRequest.setByteInput(byteInput = new NioByteInput(new ReplyInputHandler(outboundRequest, rid)));
                        connectionHandler.getConnectionContext().getConnectionProviderContext().getExecutor().execute(new InboundReplyTask(connectionHandler, outboundRequest));
                    } else {
                        byteInput = outboundRequest.getByteInput();
                    }
                }
                byteInput.push(buffer);
                return;
            }
            case RemoteProtocol.REPLY_ACK_CHUNK: {
                final int rid = buffer.getInt();
                final OutboundRequest outboundRequest;
                final IntKeyMap<OutboundRequest> outboundRequests = connectionHandler.getOutboundRequests();
                synchronized (outboundRequests) {
                    outboundRequest = outboundRequests.get(rid);
                }
                if (outboundRequest == null) {
                    RemoteConnectionHandler.log.trace("Received reply-ack-chunk for unknown request ID %d", Integer.valueOf(rid));
                    return;
                }
                synchronized (outboundRequest) {
                    outboundRequest.ack();
                }
                return;
            }
            case RemoteProtocol.REPLY_EXCEPTION: {
                final int rid = buffer.getInt();
                final byte flags = buffer.get();
                final OutboundRequest outboundRequest;
                final NioByteInput byteInput;
                final IntKeyMap<OutboundRequest> outboundRequests = connectionHandler.getOutboundRequests();
                synchronized (outboundRequests) {
                    outboundRequest = outboundRequests.get(rid);
                }
                if (outboundRequest == null) {
                    RemoteConnectionHandler.log.trace("Received reply-exception for unknown request ID %d", Integer.valueOf(rid));
                    return;
                }
                synchronized (outboundRequest) {
                    if ((flags & RemoteProtocol.MSG_FLAG_FIRST) != 0) {
                        // todo - check for duplicate
                        outboundRequest.setByteInput(byteInput = new NioByteInput(new ReplyInputHandler(outboundRequest, rid)));
                        connectionHandler.getConnectionContext().getConnectionProviderContext().getExecutor().execute(new InboundReplyExceptionTask(connectionHandler, outboundRequest));
                    } else {
                        byteInput = outboundRequest.getByteInput();
                    }
                }
                byteInput.push(buffer);
                return;
            }
            case RemoteProtocol.REPLY_EXCEPTION_ABORT: {
                final int rid = buffer.getInt();
                final OutboundRequest outboundRequest;
                final IntKeyMap<OutboundRequest> outboundRequests = connectionHandler.getOutboundRequests();
                synchronized (outboundRequests) {
                    outboundRequest = outboundRequests.get(rid);
                }
                if (outboundRequest == null) {
                    RemoteConnectionHandler.log.trace("Received reply-exception-abort for unknown request ID %d", Integer.valueOf(rid));
                    return;
                }
                final NioByteInput byteInput;
                final ReplyHandler replyHandler;
                synchronized (outboundRequest) {
                    byteInput = outboundRequest.getByteInput();
                    replyHandler = outboundRequest.getInboundReplyHandler();
                }
                final ReplyException re = new ReplyException("Reply exception was aborted");
                if (byteInput != null) {
                    byteInput.pushException(re);
                }
                if (replyHandler != null) {
                    SpiUtils.safeHandleException(replyHandler, re);
                }
                return;
            }
            default: {
                RemoteConnectionHandler.log.error("Received invalid packet type on %s, closing", connectionHandler.getChannel());
                IoUtils.safeClose(connectionHandler);
            }
        }
    }

    public void handleEof() {
        IoUtils.safeClose(remoteConnectionHandler);
    }

    public void handleException(final IOException e) {
        IoUtils.safeClose(remoteConnectionHandler);
    }
}
