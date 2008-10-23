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

import org.jboss.xnio.channels.AllocatedMessageChannel;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.Connector;
import org.jboss.xnio.Acceptor;
import org.jboss.xnio.log.Logger;
import org.jboss.remoting.spi.remote.RequestHandler;
import org.jboss.remoting.spi.remote.RequestHandlerSource;
import org.jboss.remoting.spi.remote.ReplyHandler;
import org.jboss.remoting.spi.remote.RemoteRequestContext;
import org.jboss.remoting.spi.remote.Handle;
import org.jboss.remoting.spi.SpiUtils;
import org.jboss.remoting.spi.AbstractAutoCloseable;
import org.jboss.remoting.spi.stream.StreamDetector;
import org.jboss.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.remoting.spi.stream.StreamProvider;
import static org.jboss.remoting.util.CollectionUtil.concurrentIntegerMap;
import org.jboss.remoting.util.CollectionUtil;
import org.jboss.remoting.util.ConcurrentIntegerMap;
import org.jboss.remoting.CloseHandler;
import org.jboss.remoting.Endpoint;
import org.jboss.remoting.SimpleCloseable;
import org.jboss.remoting.RemoteExecutionException;
import org.jboss.remoting.IndeterminateOutcomeException;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.ByteOutput;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.Configuration;
import org.jboss.marshalling.ObjectTable;
import org.jboss.marshalling.Marshalling;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InterruptedIOException;

/**
 * Protocol handler for the basic message-oriented Remoting protocol.
 *
 * @param <A> stream channel address type (Void if streams are not supported)
 */
public final class MultiplexHandler<A> implements IoHandler<AllocatedMessageChannel> {

    private static final Logger log = Logger.getLogger(MultiplexHandler.class);

    //--== Connection configuration items ==--
    private final MarshallerFactory marshallerFactory;
    private final Configuration marshallingConfiguration;
    private final int linkMetric;
    private final Executor executor;
    // buffer allocator for outbound message assembly
    private final BufferAllocator<ByteBuffer> allocator;
    private final StreamDetector streamDetector;
    private final Connector<A, AllocatedMessageChannel> messageConnector;
    private final Acceptor<A, AllocatedMessageChannel> messageAcceptor;
    private final Connector<A, StreamChannel> streamConnector;
    private final Acceptor<A, StreamChannel> streamAcceptor;

    // running on remote node
    private final ConcurrentIntegerMap<ReplyHandler> remoteRequests = concurrentIntegerMap();
    // running on local node
    private final ConcurrentIntegerMap<RemoteRequestContext> localRequests = concurrentIntegerMap();
    // sequence for remote requests
    private final AtomicInteger requestSequence = new AtomicInteger();

    // clients whose requests get forwarded to the remote side
    // even #s were opened from services forwarded to us (our sequence)
    // odd #s were forwarded directly to us (remote sequence)
    private final ConcurrentIntegerMap<RequestHandler> remoteClients = concurrentIntegerMap();
    // forwarded to remote side (handled on this side)
    private final ConcurrentIntegerMap<Handle<RequestHandler>> forwardedClients = concurrentIntegerMap();
    // sequence for forwarded clients (unsigned; shift left one bit, add one)
    private final AtomicInteger forwardedClientSequence = new AtomicInteger();
    // sequence for clients created from services forwarded to us (unsigned; shift left one bit)
    private final AtomicInteger remoteClientSequence = new AtomicInteger();

    // services forwarded to us
    private final ConcurrentIntegerMap<RequestHandlerSource> remoteServices = concurrentIntegerMap();
    // forwarded to remote side (handled on this side)
    private final ConcurrentIntegerMap<Handle<RequestHandlerSource>> forwardedServices = concurrentIntegerMap();
    // sequence for forwarded services
    private final AtomicInteger serviceSequence = new AtomicInteger();

    private final Endpoint endpoint;

    private volatile AllocatedMessageChannel channel;

    public MultiplexHandler(final Endpoint endpoint, final RemotingChannelConfiguration configuration, final StreamProvider<A> streamProvider) {
        this.endpoint = endpoint;
        messageConnector = streamProvider.getMessageChannelConnector();
        messageAcceptor = streamProvider.getMessageChannelAcceptor();
        streamConnector = streamProvider.getStreamChannelConnector();
        streamAcceptor = streamProvider.getStreamChannelAcceptor();
        allocator = configuration.getAllocator();
        executor = configuration.getExecutor();
        marshallerFactory = configuration.getMarshallerFactory();
        marshallingConfiguration = configuration.getMarshallingConfiguration();
        linkMetric = configuration.getLinkMetric();
        streamDetector = configuration.getStreamDetector();
    }

    public void handleOpened(final AllocatedMessageChannel channel) {
        channel.resumeReads();
    }

    public void handleReadable(final AllocatedMessageChannel channel) {
        for (;;) try {
            final ByteBuffer buffer;
            try {
                buffer = channel.receive();
            } catch (IOException e) {
                log.error(e, "I/O error in protocol channel; closing channel");
                IoUtils.safeClose(channel);
                return;
            }
            if (buffer == null) {
                // todo release all handles...
                // todo what if the write queue is not empty?
                IoUtils.safeClose(channel);
                return;
            }
            if (! buffer.hasRemaining()) {
                // would block
                channel.resumeReads();
                return;
            }
            final MessageType msgType;
            try {
                msgType = MessageType.getMessageType(buffer.get() & 0xff);
            } catch (IllegalArgumentException ex) {
                log.trace("Received invalid message type");
                return;
            }
            log.trace("Received message %s, type %s", buffer, msgType);
            switch (msgType) {
                case REQUEST: {
                    final int clientId = buffer.getInt();
                    final Handle<RequestHandler> handle = forwardedClients.get(clientId);
                    if (handle == null) {
                        log.trace("Request on invalid client ID %d", Integer.valueOf(clientId));
                        break;
                    }
                    final int requestId = buffer.getInt();
                    final Object payload;
                    try {
                        final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(marshallingConfiguration);
                        try {
                            unmarshaller.start(Marshalling.createByteInput(buffer));
                            try {
                                payload = unmarshaller.readObject();
                                unmarshaller.finish();
                            } catch (ClassNotFoundException e) {
                                log.trace("Class not found in request ID %d for client ID %d", Integer.valueOf(requestId), Integer.valueOf(clientId));
                                // todo - send request receive failed message
                                break;
                            }
                        } finally {
                            IoUtils.safeClose(unmarshaller);
                        }
                    } catch (IOException ex) {
                        log.trace("Failed to unmarshal a request (%s), sending %s", ex, MessageType.REQUEST_RECEIVE_FAILED);
                        // todo send a request failure message
                        break;
                    }
                    final RequestHandler requestHandler = handle.getResource();
                    requestHandler.receiveRequest(payload, new ReplyHandlerImpl(channel, requestId, allocator));
                    break;
                }
                case REPLY: {
                    final int requestId = buffer.getInt();
                    final ReplyHandler replyHandler = remoteRequests.remove(requestId);
                    if (replyHandler == null) {
                        log.trace("Got reply to unknown request %d", Integer.valueOf(requestId));
                        break;
                    }
                    final Object payload;
                    try {
                        final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(marshallingConfiguration);
                        try {
                            unmarshaller.start(Marshalling.createByteInput(buffer));
                            try {
                                payload = unmarshaller.readObject();
                                unmarshaller.finish();
                            } catch (ClassNotFoundException e) {
                                replyHandler.handleException(new InvalidClassException("Class not found: " + e.toString()));
                                log.trace("Class not found in reply to request ID %d", Integer.valueOf(requestId));
                                break;
                            }
                        } finally {
                            IoUtils.safeClose(unmarshaller);
                        }
                    } catch (IOException ex) {
                        log.trace("Failed to unmarshal a reply (%s), sending a ReplyException", ex);
                        // todo
                        SpiUtils.safeHandleException(replyHandler, ex);
                        break;
                    }
                    SpiUtils.safeHandleReply(replyHandler, payload);
                    break;
                }
                case CANCEL_REQUEST: {
                    final int requestId = buffer.getInt();
                    final RemoteRequestContext context = localRequests.get(requestId);
                    if (context != null) {
                        context.cancel();
                    }
                    break;
                }
                case CANCEL_ACK: {
                    final int requestId = buffer.getInt();
                    final ReplyHandler replyHandler = remoteRequests.get(requestId);
                    if (replyHandler != null) try {
                        replyHandler.handleCancellation();
                    } catch (IOException e) {
                        log.trace("Failed to forward a cancellation acknowledgement (%s)", e);
                    }
                    break;
                }
                case REQUEST_RECEIVE_FAILED: {
                    final int requestId = buffer.getInt();
                    final ReplyHandler replyHandler = remoteRequests.remove(requestId);
                    if (replyHandler == null) {
                        log.trace("Got reply to unknown request %d", Integer.valueOf(requestId));
                        break;
                    }
                    final String reason = readUTFZ(buffer);
                    
                    // todo - throw a new ReplyException
                    break;
                }
                case REQUEST_FAILED: {
                    final int requestId = buffer.getInt();
                    final ReplyHandler replyHandler = remoteRequests.remove(requestId);
                    if (replyHandler == null) {
                        log.trace("Got reply to unknown request %d", Integer.valueOf(requestId));
                        break;
                    }
                    final Throwable cause;
                    try {
                        final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(marshallingConfiguration);
                        try {
                            unmarshaller.start(Marshalling.createByteInput(buffer));
                            try {
                                cause = (Throwable) unmarshaller.readObject();
                            } catch (ClassNotFoundException e) {
                                replyHandler.handleException(new InvalidClassException("Class not found: " + e.toString()));
                                log.trace("Class not found in exception reply to request ID %d", Integer.valueOf(requestId));
                                break;
                            } catch (ClassCastException e) {
                                SpiUtils.safeHandleException(replyHandler, new RemoteExecutionException("Remote request failed (and an unexpected I/O error occurred when attempting to unmarshal the cause)"));
                                break;
                            }
                        } finally {
                            IoUtils.safeClose(unmarshaller);
                        }
                    } catch (IOException ex) {
                        log.trace("Failed to unmarshal an exception reply (%s), sending a generic execution exception");
                        SpiUtils.safeHandleException(replyHandler, new RemoteExecutionException("Remote request failed (and an unexpected I/O error occurred when attempting to read the cause)"));
                        break;
                    }
                    SpiUtils.safeHandleException(replyHandler, new RemoteExecutionException("Remote execution failed", cause));
                    break;
                }
                case REQUEST_OUTCOME_UNKNOWN: {
                    final int requestId = buffer.getInt();
                    final ReplyHandler replyHandler = remoteRequests.remove(requestId);
                    if (replyHandler == null) {
                        log.trace("Got reply to unknown request %d", Integer.valueOf(requestId));
                        break;
                    }
                    final String reason = readUTFZ(buffer);
                    SpiUtils.safeHandleException(replyHandler, new IndeterminateOutcomeException(reason));
                    break;
                }
                case CLIENT_CLOSE: {
                    final int clientId = buffer.getInt();
                    final Handle<RequestHandler> handle = forwardedClients.remove(clientId);
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
                    final Handle<RequestHandlerSource> handle = forwardedServices.get(serviceId);
                    if (handle == null) {
                        log.warn("Received client open message for unknown service %d", Integer.valueOf(serviceId));
                        break;
                    }
                    try {
                        final RequestHandlerSource requestHandlerSource = handle.getResource();
                        final Handle<RequestHandler> clientHandle = requestHandlerSource.createRequestHandler();
                        // todo check for duplicate
                        // todo validate the client ID
                        log.trace("Opening client %d from service %d", Integer.valueOf(clientId), Integer.valueOf(serviceId));
                        forwardedClients.put(clientId, clientHandle);
                    } catch (IOException ex) {
                        log.error(ex, "Failed to create a request handler for client ID %d", Integer.valueOf(clientId));
                        break;
                    } finally {
                        IoUtils.safeClose(handle);
                    }
                    break;
                }
                case SERVICE_CLOSE: {
                    final Handle<RequestHandlerSource> handle = forwardedServices.remove(buffer.getInt());
                    if (handle == null) {
                        break;
                    }
                    IoUtils.safeClose(handle);
                    break;
                }
                case SERVICE_ADVERTISE: {
                    final int serviceId = buffer.getInt();
                    final String serviceType = readUTFZ(buffer);
                    final String groupName = readUTFZ(buffer);
                    final String endpointName = readUTFZ(buffer);
                    final int baseMetric = buffer.getInt();
                    int id = -1;
                    final RequestHandlerSource handlerSource = new RequestHandlerSourceImpl(allocator, id);
                    final int calcMetric = baseMetric + linkMetric;
                    if (calcMetric > 0) {
                        try {
                            final SimpleCloseable closeable = endpoint.registerRemoteService(serviceType, groupName, endpointName, handlerSource, calcMetric);
                            // todo - something with that closeable
                        } catch (IOException e) {
                            log.error(e, "Unable to register remote service");
                        }
                    }
                    break;
                }
                case SERVICE_UNADVERTISE: {
                    final int serviceId = buffer.getInt();
                    IoUtils.safeClose(remoteServices.get(serviceId));
                    break;
                }
                default: {
                    log.error("Malformed packet received (invalid message type %s)", msgType);
                }
            }
        } catch (BufferUnderflowException e) {
            log.error("Malformed packet received (buffer underflow)");
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

    RequestHandlerSource getRemoteService(final int id) {
        return new RequestHandlerSourceImpl(allocator, id);
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

        public void handleReply(final Object reply) throws IOException {
            ByteBuffer buffer = allocator.allocate();
            buffer.put((byte) MessageType.REPLY.getId());
            buffer.putInt(requestId);
            try {
                final Marshaller marshaller = marshallerFactory.createMarshaller(marshallingConfiguration);
                try {
                    final List<ByteBuffer> bufferList = new ArrayList<ByteBuffer>();
                    final ByteOutput output = createByteOutput(allocator, bufferList);
                    try {
                        marshaller.start(output);
                        marshaller.writeObject(reply);
                        marshaller.close();
                        output.close();
                        registerWriter(channel, new SimpleWriteHandler(allocator, bufferList));
                    } finally {
                        IoUtils.safeClose(output);
                    }
                } finally {
                    IoUtils.safeClose(marshaller);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Reply handler thread interrupted before a reply could be sent");
            }
        }

        public void handleException(final IOException exception) throws IOException {
            ByteBuffer buffer = allocator.allocate();
            buffer.put((byte) MessageType.REQUEST_FAILED.getId());
            buffer.putInt(requestId);
            try {
                final Marshaller marshaller = marshallerFactory.createMarshaller(marshallingConfiguration);
                try {
                    final List<ByteBuffer> bufferList = new ArrayList<ByteBuffer>();
                    final ByteOutput output = createByteOutput(allocator, bufferList);
                    try {
                        marshaller.start(output);
                        marshaller.writeObject(exception);
                        marshaller.close();
                        output.close();
                        registerWriter(channel, new SimpleWriteHandler(allocator, bufferList));
                    } finally {
                        IoUtils.safeClose(output);
                    }
                } finally {
                    IoUtils.safeClose(marshaller);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Reply handler thread interrupted before an exception could be sent");
            }
        }

        public void handleCancellation() throws InterruptedIOException {
            final ByteBuffer buffer = allocator.allocate();
            buffer.put((byte) MessageType.CANCEL_ACK.getId());
            buffer.putInt(requestId);
            buffer.flip();
            try {
                registerWriter(channel, new SimpleWriteHandler(allocator, buffer));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Reply handler thread interrupted before cancellation could be sent");
            }
        }
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

    // Reader utils

    private String readUTFZ(ByteBuffer buffer) {
        StringBuilder builder = new StringBuilder();
        int state = 0, a = 0;
        while (buffer.hasRemaining()) {
            final int v = buffer.get() & 0xff;
            switch (state) {
                case 0: {
                    if (v == 0) {
                        return builder.toString();
                    } else if (v < 128) {
                        builder.append((char) v);
                    } else if (192 <= v && v < 224) {
                        a = v << 6;
                        state = 1;
                    } else if (224 <= v && v < 232) {
                        a = v << 12;
                        state = 2;
                    } else {
                        builder.append('?');
                    }
                    break;
                }
                case 1: {
                    if (v == 0) {
                        builder.append('?');
                        return builder.toString();
                    } else if (128 <= v && v < 192) {
                        a |= v & 0x3f;
                        builder.append((char) a);
                    } else {
                        builder.append('?');
                    }
                    state = 0;
                    break;
                }
                case 2: {
                    if (v == 0) {
                        builder.append('?');
                        return builder.toString();
                    } else if (128 <= v && v < 192) {
                        a |= (v & 0x3f) << 6;
                        state = 1;
                    } else {
                        builder.append('?');
                        state = 0;
                    }
                    break;
                }
                default:
                    throw new IllegalStateException("wrong state");
            }
        }
        return builder.toString();
    }

    // client endpoint

    private final class RequestHandlerImpl extends AbstractAutoCloseable<RequestHandler> implements RequestHandler {

        private final int identifier;
        private final BufferAllocator<ByteBuffer> allocator;

        public RequestHandlerImpl(final int identifier, final BufferAllocator<ByteBuffer> allocator) {
            super(executor);
            if (allocator == null) {
                throw new NullPointerException("allocator is null");
            }
            this.identifier = identifier;
            this.allocator = allocator;
            addCloseHandler(new CloseHandler<RequestHandler>() {
                public void handleClose(final RequestHandler closed) {
                    remoteClients.remove(identifier, this);
                    ByteBuffer buffer = allocator.allocate();
                    buffer.put((byte) MessageType.CLIENT_CLOSE.getId());
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

        public RemoteRequestContext receiveRequest(final Object request, final ReplyHandler handler) {
            log.trace("Sending outbound request of type %s", request == null ? "null" : request.getClass());
            try {
                final List<ByteBuffer> bufferList;
                final Marshaller marshaller = marshallerFactory.createMarshaller(marshallingConfiguration);
                try {
                    bufferList = new ArrayList<ByteBuffer>();
                    final ByteOutput output = createByteOutput(allocator, bufferList);
                    try {
                        marshaller.write(MessageType.REQUEST.getId());
                        marshaller.writeInt(identifier);

                        int id;
                        do {
                            id = requestSequence.getAndIncrement();
                        } while (remoteRequests.putIfAbsent(id, handler) != null);
                        marshaller.writeInt(id);
                        marshaller.writeObject(request);
                        marshaller.close();
                        output.close();
                        try {
                            registerWriter(channel, new SimpleWriteHandler(allocator, bufferList));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            executor.execute(new Runnable() {
                                public void run() {
                                    SpiUtils.safeHandleCancellation(handler);
                                }
                            });
                            return SpiUtils.getBlankRemoteRequestContext();
                        }
                        log.trace("Sent request %s", request);
                        return new RemoteRequestContextImpl(id, allocator, channel);
                    } finally {
                        IoUtils.safeClose(output);
                    }
                } finally {
                    IoUtils.safeClose(marshaller);
                }
            } catch (final IOException t) {
                log.trace(t, "receiveRequest failed with an exception");
                executor.execute(new Runnable() {
                    public void run() {
                        SpiUtils.safeHandleException(handler, t);
                    }
                });
                return SpiUtils.getBlankRemoteRequestContext();
            }
        }

        public String toString() {
            return "forwarding request handler <" + Integer.toString(hashCode(), 16) + "> (id = " + identifier + ")";
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

        public void cancel() {
            try {
                final ByteBuffer buffer = allocator.allocate();
                buffer.put((byte) MessageType.CANCEL_REQUEST.getId());
                buffer.putInt(id);
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

    public final class RequestHandlerSourceImpl extends AbstractAutoCloseable<RequestHandlerSource> implements RequestHandlerSource {

        private final BufferAllocator<ByteBuffer> allocator;
        private final int identifier;

        protected RequestHandlerSourceImpl(final BufferAllocator<ByteBuffer> allocator, final int identifier) {
            super(executor);
            this.allocator = allocator;
            this.identifier = identifier;
            addCloseHandler(new CloseHandler<RequestHandlerSource>() {
                public void handleClose(final RequestHandlerSource closed) {
                    ByteBuffer buffer = allocator.allocate();
                    buffer.put((byte) MessageType.SERVICE_CLOSE.getId());
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

        public Handle<RequestHandler> createRequestHandler() throws IOException {
            int id;
            do {
                id = remoteClientSequence.getAndIncrement() << 1;
            } while (remoteClients.putIfAbsent(id, new RequestHandlerImpl(id, MultiplexHandler.this.allocator)) != null);
            final int clientId = id;
            final ByteBuffer buffer = allocator.allocate();
            buffer.put((byte) MessageType.CLIENT_OPEN.getId());
            buffer.putInt(identifier);
            buffer.putInt(clientId);
            buffer.flip();
            // todo - probably should bail out if we're interrupted?
            boolean intr = false;
            for (;;) {
                try {
                    registerWriter(channel, new SimpleWriteHandler(allocator, buffer));
                    try {
                        return new RequestHandlerImpl(clientId, allocator).getHandle();
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
            return "forwarding request handler source <" + Integer.toString(hashCode(), 16) + "> (id = " + identifier + ")";
        }
    }

    public static ByteOutput createByteOutput(final BufferAllocator<ByteBuffer> allocator, final Collection<ByteBuffer> target) {
        return new ByteOutput() {
            private ByteBuffer current;

            private ByteBuffer getCurrent() {
                final ByteBuffer buffer = current;
                return buffer == null ? (current = allocator.allocate()) : buffer;
            }

            public void write(final int i) {
                final ByteBuffer buffer = getCurrent();
                buffer.put((byte) i);
                if (! buffer.hasRemaining()) {
                    buffer.flip();
                    target.add(buffer);
                    current = null;
                }
            }

            public void write(final byte[] bytes) {
                write(bytes, 0, bytes.length);
            }

            public void write(final byte[] bytes, int offs, int len) {
                while (len > 0) {
                    final ByteBuffer buffer = getCurrent();
                    final int c = Math.min(len, buffer.remaining());
                    buffer.put(bytes, offs, c);
                    offs += c;
                    len -= c;
                    if (! buffer.hasRemaining()) {
                        buffer.flip();
                        target.add(buffer);
                        current = null;
                    }
                }
            }

            public void close() {
                flush();
            }

            public void flush() {
                final ByteBuffer buffer = current;
                if (buffer != null) {
                    buffer.flip();
                    target.add(buffer);
                    current = null;
                }
            }
        };
    }

    public class ProtocolObjectTableWriter implements ObjectTable.Writer {

        public void writeObject(final Marshaller marshaller, final Object o) throws IOException {
            
        }
    }

    public class ProtocolObjectTable implements ObjectTable {

        public Writer getObjectWriter(final Object o) throws IOException /* fixed in beta2 */ {
            if (o instanceof RequestHandler) {
                final RequestHandler requestHandler = (RequestHandler) o;

            } else if (o instanceof RequestHandlerSource) {
                final RequestHandlerSource requestHandlerSource = (RequestHandlerSource) o;
                
            } else {
                final StreamSerializerFactory ssf = streamDetector.detectStream(o);
                if (ssf != null) {
                    final IoHandler<? super AllocatedMessageChannel> streamHandler = ssf.getLocalSide(o, new StreamContextImpl(executor, marshallerFactory, marshallingConfiguration));
                    // todo - this should really be the "server" side
                    final IoFuture<AllocatedMessageChannel> futureChannel = messageConnector.connectTo(null, streamHandler);
                    
                }
            }
            return null;
        }

        public Object readObject(final Unmarshaller unmarshaller) throws IOException, ClassNotFoundException {
            switch (unmarshaller.readByte()) {
                case 1: {
                    // remote client
                    final int id = unmarshaller.readInt();
                }
                case 2: {
                    // remote client source
                }
                case 3: {
                    // stream
                }
                default: {
                    // invalid
                }
            }
            return null;
        }
    }
}
