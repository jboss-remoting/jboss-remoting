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

import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.xnio.BufferAllocator;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.channels.AllocatedMessageChannel;
import org.jboss.remoting.spi.ReplyHandler;
import org.jboss.remoting.spi.RemoteRequestContext;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.remoting.spi.Handle;
import org.jboss.remoting.spi.NamedServiceRegistry;
import org.jboss.remoting.spi.SpiUtils;
import org.jboss.remoting.spi.AbstractHandleableCloseable;
import org.jboss.remoting.QualifiedName;
import org.jboss.remoting.Endpoint;
import org.jboss.remoting.IndeterminateOutcomeException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.InterruptedIOException;

/**
 *
 */
public final class MultiplexConnection extends AbstractHandleableCloseable<MultiplexConnection> {
    private static final Logger log = Logger.getLogger("org.jboss.remoting.multiplex");

    //--== Connection configuration items ==--
    private final MarshallerFactory marshallerFactory;
    private final MarshallingConfiguration marshallingConfiguration;
    private final int linkMetric;
    private final Executor executor;
    // buffer allocator for outbound message assembly
    private final BufferAllocator<ByteBuffer> allocator;

    // running on remote node
    private final IntegerBiMap<ReplyHandler> remoteRequests = IdentityHashIntegerBiMap.createSynchronizing();
    // running on local node
    private final IntegerBiMap<RemoteRequestContext> localRequests = IdentityHashIntegerBiMap.createSynchronizing();
    // sequence for remote requests
    private final AtomicInteger requestSequence = new AtomicInteger();

    // clients whose requests get forwarded to the remote side
    // even #s were opened from services forwarded to us (our sequence)
    // odd #s were forwarded directly to us (remote sequence)
    private final IntegerBiMap<RequestHandler> remoteClients = IdentityHashIntegerBiMap.createSynchronizing();
    // forwarded to remote side (handled on this side)
    private final IntegerResourceBiMap<RequestHandler> forwardedClients = IdentityHashIntegerResourceBiMap.createSynchronizing();
    // sequence for forwarded clients (shift left one bit, add one, limit is 2^30)
    private final AtomicInteger forwardedClientSequence = new AtomicInteger();
    // sequence for clients created from services forwarded to us (shift left one bit, limit is 2^30)
    private final AtomicInteger remoteClientSequence = new AtomicInteger();

    // services on the remote side
    private final IntegerBiMap<FutureRemoteRequestHandlerSource> remoteServices = IdentityHashIntegerBiMap.createSynchronizing();
    // forwarded to remote side (handled on this side)
    private final IntegerResourceBiMap<RequestHandlerSource> forwardedServices = IdentityHashIntegerResourceBiMap.createSynchronizing();
    // sequence for remote services
    private final AtomicInteger remoteServiceSequence = new AtomicInteger();

    // registered services by path
    private final NamedServiceRegistry namedServiceRegistry;

    private final Endpoint endpoint;

    private final AllocatedMessageChannel channel;

    public MultiplexConnection(final Endpoint endpoint, final AllocatedMessageChannel channel, final MultiplexConfiguration configuration) {
        super(configuration.getExecutor());
        this.endpoint = endpoint;
        this.channel = channel;
        marshallerFactory = configuration.getMarshallerFactory();
        if (marshallerFactory == null) {
            throw new NullPointerException("marshallerFactory is null");
        }
        marshallingConfiguration = configuration.getMarshallingConfiguration();
        if (marshallingConfiguration == null) {
            throw new NullPointerException("marshallingConfiguration is null");
        }
        linkMetric = configuration.getLinkMetric();
        executor = configuration.getExecutor();
        if (executor == null) {
            throw new NullPointerException("executor is null");
        }
        allocator = configuration.getAllocator();
        if (allocator == null) {
            throw new NullPointerException("allocator is null");
        }
        namedServiceRegistry = configuration.getNamedServiceRegistry();
        if (namedServiceRegistry == null) {
            throw new NullPointerException("namedServiceRegistry is null");
        }
    }

    // sequence methods

    int nextRequest() {
        return requestSequence.getAndIncrement() & 0x7fffffff;
    }

    int nextForwardedClient() {
        return (forwardedClientSequence.getAndIncrement() << 1 | 1) & 0x7fffffff;
    }

    int nextRemoteClient() {
        return remoteClientSequence.getAndIncrement() << 1 & 0x7fffffff;
    }

    int nextRemoteService() {
        return remoteServiceSequence.getAndIncrement() & 0x7fffffff;
    }

    void doBlockingWrite(ByteBuffer... buffers) throws IOException {
        log.trace("Sending message:\n%s", new MultiDumper(buffers));
        if (buffers.length == 1) doBlockingWrite(buffers[0]); else for (;;) {
            if (channel.send(buffers)) {
                return;
            }
            channel.awaitWritable();
        }
    }

    private static final class MultiDumper {
        private final ByteBuffer[] buffers;

        public MultiDumper(final ByteBuffer[] buffers) {
            this.buffers = buffers;
        }

        public String toString() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < buffers.length; i++) {
                ByteBuffer buffer = buffers[i];
                builder.append("Buffer ");
                builder.append(i);
                builder.append(":\n");
                try {
                    Buffers.dump(buffer, builder, 8, 1);
                } catch (IOException e) {
                    // ignore
                }
            }
            return builder.toString();
        }
    }

    void doBlockingWrite(ByteBuffer buffer) throws IOException {
        log.trace("Sending message:\n%s", Buffers.createDumper(buffer, 8, 1));
        for (;;) {
            if (channel.send(buffer)) {
                return;
            }
            channel.awaitWritable();
        }
    }

    void doBlockingWrite(List<ByteBuffer> buffers) throws IOException {
        doBlockingWrite(buffers.toArray(new ByteBuffer[buffers.size()]));
    }

    MarshallerFactory getMarshallerFactory() {
        return marshallerFactory;
    }

    MarshallingConfiguration getMarshallingConfiguration() {
        return marshallingConfiguration;
    }

    int getLinkMetric() {
        return linkMetric;
    }

    protected Executor getExecutor() {
        return executor;
    }

    BufferAllocator<ByteBuffer> getAllocator() {
        return allocator;
    }

    Endpoint getEndpoint() {
        return endpoint;
    }

    AllocatedMessageChannel getChannel() {
        return channel;
    }

    void removeRemoteClient(final int identifier) {
        remoteClients.remove(identifier);
    }

    void addRemoteRequest(final int id, final ReplyHandler handler) {
        remoteRequests.put(id, handler);
    }

    void addRemoteClient(final int id, final RequestHandler handler) {
        remoteClients.put(id, handler);
    }

    Handle<RequestHandler> getForwardedClient(final int id) {
        return forwardedClients.get(id);
    }

    ReplyHandler removeRemoteRequest(final int id) {
        return remoteRequests.remove(id);
    }

    RemoteRequestContext getLocalRequest(final int id) {
        return localRequests.get(id);
    }

    ReplyHandler getRemoteRequest(final int id) {
        return remoteRequests.get(id);
    }

    Handle<RequestHandler> removeForwardedClient(final int id) {
        return forwardedClients.remove(id);
    }

    Handle<RequestHandlerSource> getForwardedService(final int id) {
        return forwardedServices.get(id);
    }

    void addForwardedClient(final int id, final Handle<RequestHandler> handle) {
        forwardedClients.put(id, handle);
    }

    void addForwadedService(final int id, final Handle<RequestHandlerSource> service) {
        forwardedServices.put(id, service);
    }

    Handle<RequestHandlerSource> removeForwardedService(final int id) {
        return forwardedServices.remove(id);
    }

    Handle<RequestHandlerSource> getServiceByPath(String path) {
        return getService(QualifiedName.parse(path));
    }

    Handle<RequestHandlerSource> getService(final QualifiedName name) {
        return namedServiceRegistry.lookupService(name);
    }

    FutureRemoteRequestHandlerSource getFutureRemoteService(final int id) {
        return remoteServices.get(id);
    }

    FutureRemoteRequestHandlerSource removeFutureRemoteService(final int id) {
        return remoteServices.remove(id);
    }

    public Handle<RequestHandlerSource> openRemoteService(final QualifiedName name) throws IOException {
        log.trace("Sending request to open remote service \"%s\"", name);
        final FutureRemoteRequestHandlerSource future = new FutureRemoteRequestHandlerSource();
        int id;
        for (;;) {
            id = nextRemoteService();
            if (remoteServices.putIfAbsent(id, future)) {
                break;
            }
        }
        ByteBuffer buffer = ByteBuffer.allocate(5 + getByteLength(name));
        buffer.put((byte) MessageType.SERVICE_OPEN_REQUEST.getId());
        buffer.putInt(id);
        putQualifiedName(buffer, name);
        buffer.flip();
        doBlockingWrite(buffer);
        try {
            final Handle<RequestHandlerSource> handle = future.getInterruptibly().getHandle();
            log.trace("Opened %s", handle);
            return handle;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting for remote service");
        }
    }

    static int getByteLength(QualifiedName name) {
        int cnt = 2; // short header
        for (String s : name) {
            cnt += getByteLength(s);
        }
        return cnt;
    }

    static int getByteLength(String s) {
        final int len = s.length();
        int cnt = 0;
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            if (ch > 0 && ch <= 0x7f) {
                cnt ++;
            } else if (ch <= 0x07ff) {
                cnt += 2;
            } else {
                cnt += 3;
            }
        }
        // null terminator...
        cnt ++;
        return cnt;
    }

    static String getString(final ByteBuffer buffer) {
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

    static void putString(final ByteBuffer buffer, final String string) {
        final int len = string.length();
        for (int i = 0; i < len; i ++) {
            char ch = string.charAt(i);
            if (ch > 0 && ch <= 0x7f) {
                buffer.put((byte) ch);
            } else if (ch <= 0x07ff) {
                buffer.put((byte) (0xc0 | 0x1f & ch >> 6));
                buffer.put((byte) (0x80 | 0x3f & ch));
            } else {
                buffer.put((byte) (0xe0 | 0x0f & ch >> 12));
                buffer.put((byte) (0x80 | 0x3f & ch >> 6));
                buffer.put((byte) (0x80 | 0x3f & ch));
            }
        }
        buffer.put((byte) 0);
    }

    static QualifiedName getQualifiedName(final ByteBuffer buffer) {
        final int len = buffer.getShort() & 0xffff;
        final String[] segs = new String[len];
        for (int i = 0; i < len; i++) {
            segs[i] = getString(buffer);
        }
        return new QualifiedName(segs);
    }

    static void putQualifiedName(final ByteBuffer buffer, final QualifiedName qualifiedName) {
        final int len = qualifiedName.length();
        if (len > 0xffff) {
            throw new IllegalArgumentException("Qualified name is too long");
        }
        buffer.putShort((short) len);
        for (String seg : qualifiedName) {
            putString(buffer, seg);
        }
    }

    protected void closeAction() {
        // just to make sure...
        IoUtils.safeClose(channel);
        final IndeterminateOutcomeException ioe = new IndeterminateOutcomeException("The connection was closed");
        // Things running remotely
        for (ReplyHandler x : remoteRequests.getKeys()) {
            SpiUtils.safeHandleException(x, ioe);
        }
        for (RequestHandler x : remoteClients.getKeys()) {
            IoUtils.safeClose(x);
        }
        for (FutureRemoteRequestHandlerSource future : remoteServices.getKeys()) {
            future.addNotifier(IoUtils.<RequestHandlerSource>closingNotifier(), null);
        }
        // Things running locally
        for (RemoteRequestContext localRequest : localRequests.getKeys()) {
            localRequest.cancel();
        }
        for (Handle<RequestHandler> client : forwardedClients.getKeys()) {
            IoUtils.safeClose(client);
        }
        for (Handle<RequestHandlerSource> service : forwardedServices.getKeys()) {
            IoUtils.safeClose(service);
        }
    }

    public String toString() {
        return "multiplex connection <" + Integer.toHexString(hashCode()) + "> via " + channel;
    }
}
