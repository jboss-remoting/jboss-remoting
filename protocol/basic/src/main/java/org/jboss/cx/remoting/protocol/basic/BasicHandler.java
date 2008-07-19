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

package org.jboss.cx.remoting.protocol.basic;

import org.jboss.xnio.channels.AllocatedMessageChannel;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;
import static org.jboss.xnio.Buffers.*;
import org.jboss.cx.remoting.spi.remote.RemoteClientEndpoint;
import org.jboss.cx.remoting.spi.remote.RemoteServiceEndpoint;
import org.jboss.cx.remoting.spi.remote.ReplyHandler;
import org.jboss.cx.remoting.spi.remote.RemoteRequestContext;
import org.jboss.cx.remoting.spi.remote.Handle;
import org.jboss.cx.remoting.spi.remote.RemoteClientEndpointListener;
import org.jboss.cx.remoting.spi.marshal.Unmarshaller;
import org.jboss.cx.remoting.spi.marshal.Marshaller;
import org.jboss.cx.remoting.spi.marshal.MarshallerFactory;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.cx.remoting.spi.marshal.IdentityResolver;
import org.jboss.cx.remoting.spi.SpiUtils;
import org.jboss.cx.remoting.spi.AbstractAutoCloseable;
import static org.jboss.cx.remoting.util.CollectionUtil.concurrentMap;
import org.jboss.cx.remoting.util.CollectionUtil;
import static org.jboss.cx.remoting.protocol.basic.MessageType.REQUEST_ONEWAY;
import static org.jboss.cx.remoting.protocol.basic.MessageType.REQUEST;
import static org.jboss.cx.remoting.protocol.basic.MessageType.REPLY;
import static org.jboss.cx.remoting.protocol.basic.MessageType.CLIENT_CLOSE;
import static org.jboss.cx.remoting.protocol.basic.MessageType.CLIENT_OPEN;
import static org.jboss.cx.remoting.protocol.basic.MessageType.SERVICE_CLOSE;
import static org.jboss.cx.remoting.protocol.basic.MessageType.REQUEST_FAILED;
import static org.jboss.cx.remoting.protocol.basic.MessageType.CANCEL_ACK;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.CloseHandler;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.io.IOException;

/**
 *
 */
public final class BasicHandler implements IoHandler<AllocatedMessageChannel> {

    private static final Logger log = Logger.getLogger(BasicHandler.class);

    // clients whose requests get forwarded to the remote side
    private final ConcurrentMap<Integer, RemoteClientEndpoint> remoteClients = concurrentMap();
    // running on remote node
    private final ConcurrentMap<Integer, ReplyHandler> outstandingRequests = concurrentMap();
    // forwarded to remote side
    private final ConcurrentMap<Integer, Handle<RemoteClientEndpoint>> forwardedClients = concurrentMap();
    // forwarded to remote side
    private final ConcurrentMap<Integer, Handle<RemoteServiceEndpoint>> forwardedServices = concurrentMap();

    private final boolean server;
    private final BufferAllocator<ByteBuffer> allocator;

    private final AtomicBoolean isnew = new AtomicBoolean(true);
    private volatile AllocatedMessageChannel channel;
    private final Executor executor;
    private final MarshallerFactory<ByteBuffer> marshallerFactory;
    private final ObjectResolver resolver;
    private final ClassLoader classLoader;

    public BasicHandler(final boolean server, final BufferAllocator<ByteBuffer> allocator, final RemoteClientEndpoint root, final Executor executor, final RemoteClientEndpointListener remoteListener, final MarshallerFactory<ByteBuffer> marshallerFactory) throws RemotingException {
        this.server = server;
        this.allocator = allocator;
        this.executor = executor;
        forwardedClients.put(Integer.valueOf(0), ((RemoteClientEndpoint)root).getHandle());
        final RemoteClientEndpointImpl endpoint = new RemoteClientEndpointImpl(0, marshallerFactory, allocator);
        remoteClients.put(Integer.valueOf(0), endpoint);
        if (remoteListener != null) {
            remoteListener.notifyCreated(endpoint);
        }
        this.marshallerFactory = marshallerFactory;
        // todo
        resolver = IdentityResolver.getInstance();
        classLoader = getClass().getClassLoader();
    }

    /**
     * Sequence number of requests originating locally.
     */
    private final AtomicInteger localRequestIdSeq = new AtomicInteger();
    /**
     * Sequence number of local clients forwarded to the remote side.
     */
    private final AtomicInteger localClientIdSeq = new AtomicInteger(1);
    /**
     * Sequence number of remote clients opened locally from services from the remote side.
     */
    private final AtomicInteger remoteClientIdSeq = new AtomicInteger(1);
    /**
     * Sequence number of services forwarded to the remote side.
     */
    private final AtomicInteger localServiceIdSeq = new AtomicInteger();

    public void handleOpened(final AllocatedMessageChannel channel) {
        if (isnew.getAndSet(false)) {
            this.channel = channel;
        }
        channel.resumeReads();
    }

    public void handleReadable(final AllocatedMessageChannel channel) {
        for (;;) try {
            final ByteBuffer buffer = channel.receive();
            if (buffer == null) {
                // todo release all handles...
                IoUtils.safeClose(channel);
                return;
            }
            if (! buffer.hasRemaining()) {
                // would block
                channel.resumeReads();
                return;
            }
            int msgType = buffer.get() & 0xff;
            log.trace("Received message %s, type %d", buffer, Integer.valueOf(msgType));
            switch (msgType) {
                case REQUEST_ONEWAY: {
                    final int clientId = buffer.getInt();
                    final Handle<RemoteClientEndpoint> handle = getForwardedClient(clientId);
                    if (handle == null) {
                        log.trace("Request on invalid client ID %d", Integer.valueOf(clientId));
                        return;
                    }
                    final Unmarshaller<ByteBuffer> unmarshaller = marshallerFactory.createUnmarshaller(resolver, classLoader);
                    if (! unmarshaller.unmarshal(buffer)) {
                        log.trace("Incomplete one-way request for client ID %d", Integer.valueOf(clientId));
                        break;
                    }
                    final Object payload;
                    try {
                        payload = unmarshaller.get();
                    } catch (ClassNotFoundException e) {
                        log.trace("Class not found in one-way request for client ID %d", Integer.valueOf(clientId));
                        break;
                    }
                    final RemoteClientEndpoint clientEndpoint = handle.getResource();
                    clientEndpoint.receiveRequest(payload);
                    break;
                }
                case REQUEST: {
                    final int clientId = buffer.getInt();
                    final Handle<RemoteClientEndpoint> handle = getForwardedClient(clientId);
                    if (handle == null) {
                        log.trace("Request on invalid client ID %d", Integer.valueOf(clientId));
                        break;
                    }
                    final int requestId = buffer.getInt();
                    final Unmarshaller<ByteBuffer> unmarshaller = marshallerFactory.createUnmarshaller(resolver, classLoader);
                    if (! unmarshaller.unmarshal(buffer)) {
                        log.trace("Incomplete request ID %d for client ID %d", Integer.valueOf(requestId), Integer.valueOf(clientId));
                        new ReplyHandlerImpl(channel, requestId, allocator).handleException("Incomplete request", null);
                        break;
                    }
                    final Object payload;
                    try {
                        payload = unmarshaller.get();
                    } catch (ClassNotFoundException e) {
                        log.trace("Class not found in request ID %d for client ID %d", Integer.valueOf(requestId), Integer.valueOf(clientId));
                        break;
                    }
                    final RemoteClientEndpoint clientEndpoint = handle.getResource();
                    clientEndpoint.receiveRequest(payload, (ReplyHandler) new ReplyHandlerImpl(channel, requestId, allocator));
                    break;
                }
                case REPLY: {
                    final int requestId = buffer.getInt();
                    final ReplyHandler replyHandler = takeOutstandingReqeust(requestId);
                    if (replyHandler == null) {
                        log.trace("Got reply to unknown request %d", Integer.valueOf(requestId));
                        break;
                    }
                    final Unmarshaller<ByteBuffer> unmarshaller = marshallerFactory.createUnmarshaller(resolver, classLoader);
                    if (! unmarshaller.unmarshal(buffer)) {
                        replyHandler.handleException("Incomplete reply", null);
                        log.trace("Incomplete reply to request ID %d", Integer.valueOf(requestId));
                        break;
                    }
                    final Object payload;
                    try {
                        payload = unmarshaller.get();
                    } catch (ClassNotFoundException e) {
                        replyHandler.handleException("Reply unmarshalling failed", e);
                        log.trace("Class not found in reply to request ID %d", Integer.valueOf(requestId));
                        break;
                    }
                    SpiUtils.safeHandleReply(replyHandler, payload);
                    break;
                }
                case REQUEST_FAILED: {
                    final int requestId = buffer.getInt();
                    final ReplyHandler replyHandler = takeOutstandingReqeust(requestId);
                    if (replyHandler == null) {
                        log.trace("Got reply to unknown request %d", Integer.valueOf(requestId));
                        break;
                    }
                    final Unmarshaller<ByteBuffer> unmarshaller = marshallerFactory.createUnmarshaller(resolver, classLoader);
                    if (! unmarshaller.unmarshal(buffer)) {
                        replyHandler.handleException("Incomplete exception reply", null);
                        log.trace("Incomplete exception reply to request ID %d", Integer.valueOf(requestId));
                        break;
                    }
                    final Object message;
                    try {
                        message = unmarshaller.get();
                    } catch (ClassNotFoundException e) {
                        replyHandler.handleException("Exception reply unmarshalling failed", e);
                        log.trace("Class not found in exception reply to request ID %d", Integer.valueOf(requestId));
                        break;
                    }
                    final Object cause;
                    try {
                        cause = unmarshaller.get();
                    } catch (ClassNotFoundException e) {
                        replyHandler.handleException("Exception reply unmarshalling failed", e);
                        log.trace("Class not found in exception reply to request ID %d", Integer.valueOf(requestId));
                        break;
                    }
                    SpiUtils.safeHandleException(replyHandler, message == null ? null : message.toString(), cause instanceof Throwable ? (Throwable) cause : null);
                    break;
                }
                case CLIENT_CLOSE: {
                    final int clientId = buffer.getInt();
                    final Handle<RemoteClientEndpoint> handle = takeForwardedClient(clientId);
                    if (handle == null) {
                        log.warn("Got client close message for unknown client %d", Integer.valueOf(clientId));
                        break;
                    }
                    IoUtils.safeClose(handle);
                    break;
                }
                case CLIENT_OPEN: {
                    final int serviceId = buffer.getInt();
                    final int clientId = buffer.getInt();
                    final Handle<RemoteServiceEndpoint> handle = getForwardedService(serviceId);
                    if (handle == null) {
                        // todo log invalid request
                        break;
                    }
                    final RemoteServiceEndpoint serviceEndpoint = handle.getResource();

                    break;
                }
                case SERVICE_CLOSE: {
                    final int serviceId = buffer.getInt();
                    final Handle<RemoteServiceEndpoint> handle = takeForwardedService(serviceId);
                    if (handle == null) {
                        log.warn("Got client close message for unknown service %d", Integer.valueOf(serviceId));
                        break;
                    }
                    IoUtils.safeClose(handle);
                    break;
                }
                default: {
                    log.trace("Received invalid message type %d", Integer.valueOf(msgType));
                }
            }
        } catch (IOException e) {
            log.error(e, "I/O error in protocol channel");
            IoUtils.safeClose(channel);
            return;
        } catch (BufferUnderflowException e) {
            log.error(e, "Malformed packet");
        } catch (Throwable t) {
            log.error(t, "Handler failed");
        }
    }

    public void handleWritable(final AllocatedMessageChannel channel) {
        for (;;) {
            final WriteHandler handler = outputQueue.peek();
            if (handler == null) {
                return;
            }
            try {
                if (handler.handleWrite(channel)) {
                    log.trace("Handled write with handler %s", handler);
                    pending.decrementAndGet();
                    outputQueue.remove();
                } else {
                    channel.resumeWrites();
                    return;
                }
            } catch (Throwable t) {
                pending.decrementAndGet();
                outputQueue.remove();
            }
        }
    }

    public void handleClosed(final AllocatedMessageChannel channel) {
    }

    RemoteClientEndpoint getRemoteClient(final int i) {
        return remoteClients.get(Integer.valueOf(i));
    }

    private final class ReplyHandlerImpl implements ReplyHandler {

        private final AllocatedMessageChannel channel;
        private final int requestId;
        private final BufferAllocator<ByteBuffer> allocator;

        private ReplyHandlerImpl(final AllocatedMessageChannel channel, final int requestId, final BufferAllocator<ByteBuffer> allocator) {
            if (channel == null) {
                throw new NullPointerException("channel is null");
            }
            if (allocator == null) {
                throw new NullPointerException("allocator is null");
            }
            this.channel = channel;
            this.requestId = requestId;
            this.allocator = allocator;
        }

        public void handleReply(final Object reply) {
            ByteBuffer buffer = allocator.allocate();
            buffer.put((byte) REPLY);
            buffer.putInt(requestId);
            try {
                final Marshaller<ByteBuffer> marshaller = marshallerFactory.createMarshaller(resolver);
                marshaller.start(reply);
                final List<ByteBuffer> bufferList = new ArrayList<ByteBuffer>();
                while (! marshaller.marshal(buffer)) {
                    bufferList.add(flip(buffer));
                    buffer = allocator.allocate();
                }
                bufferList.add(flip(buffer));
                registerWriter(channel, new SimpleWriteHandler(allocator, bufferList));
            } catch (IOException e) {
                // todo log
            } catch (InterruptedException e) {
                // todo log
                Thread.currentThread().interrupt();
            }
        }

        public void handleException(final String msg, final Throwable cause) {
            ByteBuffer buffer = allocator.allocate();
            buffer.put((byte) REQUEST_FAILED);
            buffer.putInt(requestId);
            try {
                final Marshaller<ByteBuffer> marshaller = marshallerFactory.createMarshaller(resolver);
                final List<ByteBuffer> bufferList = new ArrayList<ByteBuffer>();
                marshaller.start(msg);
                while (! marshaller.marshal(buffer)) {
                    bufferList.add(flip(buffer));
                    buffer = allocator.allocate();
                }
                marshaller.start(cause);
                while (! marshaller.marshal(buffer)) {
                    bufferList.add(flip(buffer));
                    buffer = allocator.allocate();
                }
                bufferList.add(flip(buffer));
                registerWriter(channel, new SimpleWriteHandler(allocator, bufferList));
            } catch (IOException e) {
                // todo log
            } catch (InterruptedException e) {
                // todo log
                Thread.currentThread().interrupt();
            }
        }

        public void handleCancellation() {
            final ByteBuffer buffer = allocator.allocate();
            buffer.put((byte) CANCEL_ACK);
            buffer.putInt(requestId);
            buffer.flip();
            try {
                registerWriter(channel, new SimpleWriteHandler(allocator, buffer));
            } catch (InterruptedException e) {
                // todo log
                Thread.currentThread().interrupt();
            }
        }
    }

    // Session mgmt

    public int openRequest(ReplyHandler handler) {
        int id;
        do {
            id = localRequestIdSeq.getAndIncrement();
        } while (outstandingRequests.putIfAbsent(Integer.valueOf(id), handler) != null);
        return id;
    }

    public int openClientFromService() {
        int id;
        do {
            id = remoteClientIdSeq.getAndIncrement() << 1 | (server ? 1 : 0);
        } while (remoteClients.putIfAbsent(Integer.valueOf(id), new RemoteClientEndpointImpl(id, null, allocator)) != null);
        return id;
    }

    public void openClientForForwardedService(int id, RemoteClientEndpoint clientEndpoint) {
        try {
            forwardedClients.put(Integer.valueOf(id), clientEndpoint.getHandle());
        } catch (RemotingException e) {
            // TODO fix
            e.printStackTrace();
        }
    }

    public Handle<RemoteClientEndpoint> getForwardedClient(int id) {
        return forwardedClients.get(Integer.valueOf(id));
    }

    private Handle<RemoteClientEndpoint> takeForwardedClient(final int id) {
        return forwardedClients.remove(Integer.valueOf(id));
    }

    public ReplyHandler takeOutstandingReqeust(int id) {
        return outstandingRequests.remove(Integer.valueOf(id));
    }

    public Handle<RemoteServiceEndpoint> getForwardedService(final int id) {
        return forwardedServices.get(Integer.valueOf(id));
    }

    public Handle<RemoteServiceEndpoint> takeForwardedService(final int id) {
        return forwardedServices.remove(Integer.valueOf(id));
    }

    // Writer members

    private final BlockingQueue<WriteHandler> outputQueue = CollectionUtil.blockingQueue(64);
    private final AtomicInteger pending = new AtomicInteger();

    private void registerWriter(final AllocatedMessageChannel channel, final WriteHandler writeHandler) throws InterruptedException {
        outputQueue.put(writeHandler);
        if (pending.getAndIncrement() == 0) {
            channel.resumeWrites();
        }
    }

    // client endpoint

    private final class RemoteClientEndpointImpl extends AbstractAutoCloseable<RemoteClientEndpoint> implements RemoteClientEndpoint {

        private final int identifier;
        private final MarshallerFactory<ByteBuffer> marshallerFactory;
        private final BufferAllocator<ByteBuffer> allocator;

        public RemoteClientEndpointImpl(final int identifier, final MarshallerFactory<ByteBuffer> marshallerFactory, final BufferAllocator<ByteBuffer> allocator) {
            super(executor);
            if (marshallerFactory == null) {
                throw new NullPointerException("marshallerFactory is null");
            }
            if (allocator == null) {
                throw new NullPointerException("allocator is null");
            }
            this.identifier = identifier;
            this.marshallerFactory = marshallerFactory;
            this.allocator = allocator;
            addCloseHandler(new CloseHandler<RemoteClientEndpoint>() {
                public void handleClose(final RemoteClientEndpoint closed) {
                    ByteBuffer buffer = allocator.allocate();
                    buffer.put((byte) MessageType.CLIENT_CLOSE);
                    buffer.putInt(identifier);
                    buffer.flip();
                    try {
                        registerWriter(channel, new SimpleWriteHandler(allocator, buffer));
                    } catch (InterruptedException e) {
                        log.warn("Client close notification was interrupted before it could be sent");
                    }
                }
            });
        }

        public void receiveRequest(final Object request) {
            log.trace("Received one-way request of type %s", request == null ? "null" : request.getClass());
            try {
                final Marshaller<ByteBuffer> marshaller = marshallerFactory.createMarshaller(null);
                final List<ByteBuffer> bufferList = new ArrayList<ByteBuffer>();
                ByteBuffer buffer = allocator.allocate();
                buffer.put((byte) MessageType.REQUEST_ONEWAY);
                buffer.putInt(identifier);
                marshaller.start(request);
                while (! marshaller.marshal(buffer)) {
                    bufferList.add(flip(buffer));
                    buffer = allocator.allocate();
                }
                bufferList.add(flip(buffer));
                try {
                    registerWriter(channel, new SimpleWriteHandler(allocator, bufferList));
                } catch (InterruptedException e) {
                    log.trace(e, "receiveRequest was interrupted");
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (Throwable t) {
                // ignore
                log.trace(t, "receiveRequest failed with an exception");
                return;
            }
        }

        public RemoteRequestContext receiveRequest(final Object request, final ReplyHandler handler) {
            log.trace("Received request of type %s", request == null ? "null" : request.getClass());
            try {
                final Marshaller<ByteBuffer> marshaller = marshallerFactory.createMarshaller(null);
                final List<ByteBuffer> bufferList = new ArrayList<ByteBuffer>();
                ByteBuffer buffer = allocator.allocate();
                buffer.put((byte) MessageType.REQUEST);
                buffer.putInt(identifier);
                final int id = openRequest(handler);
                buffer.putInt(id);
                marshaller.start(request);
                while (! marshaller.marshal(buffer)) {
                    bufferList.add(flip(buffer));
                    buffer = allocator.allocate();
                }
                bufferList.add(flip(buffer));
                try {
                    registerWriter(channel, new SimpleWriteHandler(allocator, bufferList));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    SpiUtils.safeHandleCancellation(handler);
                    return SpiUtils.getBlankRemoteRequestContext();
                }
                log.trace("Sent request %s", request);
                return new RemoteRequestContextImpl(id, allocator, channel);
            } catch (Throwable t) {
                log.trace(t, "receiveRequest failed with an exception");
                SpiUtils.safeHandleException(handler, "Failed to build request", t);
                return SpiUtils.getBlankRemoteRequestContext();
            }
        }

        public String toString() {
            return "forwarded client endpoint (id = " + identifier + ")";
        }
    }

    public final class RemoteRequestContextImpl implements RemoteRequestContext {

        private final BufferAllocator<ByteBuffer> allocator;
        private final int id;
        private final AllocatedMessageChannel channel;

        public RemoteRequestContextImpl(final int id, final BufferAllocator<ByteBuffer> allocator, final AllocatedMessageChannel channel) {
            this.id = id;
            this.allocator = allocator;
            this.channel = channel;
        }

        public void cancel(final boolean mayInterrupt) {
            try {
                final ByteBuffer buffer = allocator.allocate();
                buffer.put((byte) MessageType.CANCEL_REQUEST);
                buffer.putInt(id);
                buffer.put((byte) (mayInterrupt ? 1 : 0));
                buffer.flip();
                registerWriter(channel, new SimpleWriteHandler(allocator, buffer));
            } catch (InterruptedException e) {
                // todo log that cancel attempt failed
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                // todo log that cancel attempt failed
            }
        }
    }

    public final class RemoteServiceEndpointImpl extends AbstractAutoCloseable<RemoteServiceEndpoint> implements RemoteServiceEndpoint {

        private final MarshallerFactory<ByteBuffer> marshallerFactory;
        private final BufferAllocator<ByteBuffer> allocator;
        private final int identifier;

        protected RemoteServiceEndpointImpl(final MarshallerFactory<ByteBuffer> marshallerFactory, final BufferAllocator<ByteBuffer> allocator, final int identifier) {
            super(executor);
            this.marshallerFactory = marshallerFactory;
            this.allocator = allocator;
            this.identifier = identifier;
            addCloseHandler(new CloseHandler<RemoteServiceEndpoint>() {
                public void handleClose(final RemoteServiceEndpoint closed) {
                    ByteBuffer buffer = allocator.allocate();
                    buffer.put((byte) MessageType.SERVICE_CLOSE);
                    buffer.putInt(identifier);
                    buffer.flip();
                    try {
                        registerWriter(channel, new SimpleWriteHandler(allocator, buffer));
                    } catch (InterruptedException e) {
                        log.warn("Service close notification was interrupted before it could be sent");
                    }
                }
            });
        }

        public Handle<RemoteClientEndpoint> createClientEndpoint() throws RemotingException {
            final int id = openClientFromService();
            final ByteBuffer buffer = allocator.allocate();
            buffer.putInt(identifier);
            buffer.putInt(openClientFromService());
            buffer.flip();
            // todo - probably should bail out if we're interrupted?
            boolean intr = false;
            for (;;) {
                try {
                    registerWriter(channel, new SimpleWriteHandler(allocator, buffer));
                    try {
                        return new RemoteClientEndpointImpl(id, marshallerFactory, allocator).getHandle();
                    } finally {
                        if (intr) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } catch (InterruptedException e) {
                    intr = true;
                }
            }
        }

        public String toString() {
            return "forwarded service endpoint (id = " + identifier + ")";
        }
    }
}
