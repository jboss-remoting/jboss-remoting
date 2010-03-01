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
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.util.IntKeyMap;
import org.jboss.remoting3.IndeterminateOutcomeException;
import org.jboss.remoting3.ServiceOpenException;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.RequestHandlerConnector;
import org.jboss.remoting3.spi.SpiUtils;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.Cancellable;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Pool;
import org.jboss.xnio.Result;
import org.jboss.xnio.log.Logger;

final class RemoteConnectionHandler extends AbstractHandleableCloseable<RemoteConnectionHandler> implements ConnectionHandler {

    static final Logger log = Logger.getLogger("org.jboss.remoting.remote");

    static final int LENGTH_PLACEHOLDER = 0;

    private final Pool<ByteBuffer> bufferPool = Buffers.createHeapByteBufferAllocator(4096);
    private final MarshallerFactory marshallerFactory;
    private final MarshallingConfiguration marshallingConfiguration;

    private final ConnectionHandlerContext connectionContext;
    private final RemoteConnection remoteConnection;
    private final Random random = new Random();

    private final IntKeyMap<OutboundClient> outboundClients = new IntKeyMap<OutboundClient>();
    private final IntKeyMap<InboundClient> inboundClients = new IntKeyMap<InboundClient>();

    private final IntKeyMap<OutboundRequest> outboundRequests = new IntKeyMap<OutboundRequest>();
    private final IntKeyMap<InboundRequest> inboundRequests = new IntKeyMap<InboundRequest>();

    private final AtomicBoolean closed = new AtomicBoolean();

    public RemoteConnectionHandler(final ConnectionHandlerContext connectionContext, final RemoteConnection remoteConnection, final MarshallerFactory marshallerFactory) {
        super(connectionContext.getConnectionProviderContext().getExecutor());
        this.connectionContext = connectionContext;
        this.remoteConnection = remoteConnection;
        this.marshallerFactory = marshallerFactory;
        final MarshallingConfiguration config = new MarshallingConfiguration();
        config.setClassExternalizerFactory(PrimaryExternalizerFactory.INSTANCE);
        config.setObjectTable(new PrimaryObjectTable(connectionContext.getConnectionProviderContext().getEndpoint()));
        config.setStreamHeader(Marshalling.nullStreamHeader());
        // fixed for now (v0)
        config.setVersion(2);
        this.marshallingConfiguration = config;
    }

    public Cancellable open(final String serviceType, final String groupName, final Result<RequestHandler> result) {
        final OutboundClient outboundClient;
        int id;
        synchronized (outboundClients) {
            while (outboundClients.containsKey(id = random.nextInt() | 1));
            outboundClient = new OutboundClient(this, id, result, serviceType, groupName);
            outboundClients.put(id, outboundClient);
        }
        // compose & send message
        final ByteBuffer buffer = bufferPool.allocate();
        try {
            buffer.putInt(LENGTH_PLACEHOLDER);
            buffer.put(RemoteProtocol.SERVICE_REQUEST);
            buffer.putInt(id);
            Buffers.putModifiedUtf8(buffer, serviceType);
            buffer.put((byte) 0);
            Buffers.putModifiedUtf8(buffer, groupName);
            buffer.put((byte) 0);
            buffer.flip();
            remoteConnection.sendBlocking(buffer);
        } catch (IOException e) {
            result.setException(e);
        } catch (Throwable e) {
            result.setException(new ServiceOpenException("Failed to open service", e));
        } finally {
            bufferPool.free(buffer);
        }
        return outboundClient;
    }

    public RequestHandlerConnector createConnector(final RequestHandler localHandler) {
        final InboundClient inboundClient = new InboundClient(this, localHandler);
        int id;
        synchronized (inboundClients) {
            while (inboundClients.containsKey(id = random.nextInt() & ~1));
            inboundClients.put(id, inboundClient);
        }
        return new UnsentRequestHandlerConnector(id, this);
    }

    protected void closeAction() throws IOException {
        try {
            remoteConnection.close();
        } finally {
            // other actions here
            for (IntKeyMap.Entry<OutboundClient> entry : outboundClients) {
                final OutboundClient outboundClient = entry.getValue();
                synchronized (outboundClient) {
                    IoUtils.safeClose(outboundClient.getRequestHandler());
                }
            }
            for (IntKeyMap.Entry<InboundClient> entry : inboundClients) {
                final InboundClient inboundClient = entry.getValue();
                synchronized (inboundClient) {
                    IoUtils.safeClose(inboundClient.getHandler());
                }
            }
            for (IntKeyMap.Entry<OutboundRequest> entry : outboundRequests) {
                final OutboundRequest outboundRequest = entry.getValue();
                synchronized (outboundRequest) {
                    SpiUtils.safeHandleException(outboundRequest.getInboundReplyHandler(), new IndeterminateOutcomeException("Connection closed"));
                }
            }
            for (IntKeyMap.Entry<InboundRequest> entry : inboundRequests) {
                final InboundRequest inboundRequest = entry.getValue();
                synchronized (inboundRequest) {
                    inboundRequest.getCancellable().cancel();
                }
            }
        }
    }

    Pool<ByteBuffer> getBufferPool() {
        return bufferPool;
    }

    MarshallerFactory getMarshallerFactory() {
        return marshallerFactory;
    }

    MarshallingConfiguration getMarshallingConfiguration() {
        return marshallingConfiguration;
    }

    ConnectionHandlerContext getConnectionContext() {
        return connectionContext;
    }

    Random getRandom() {
        return random;
    }

    IntKeyMap<OutboundClient> getOutboundClients() {
        return outboundClients;
    }

    IntKeyMap<InboundClient> getInboundClients() {
        return inboundClients;
    }

    IntKeyMap<OutboundRequest> getOutboundRequests() {
        return outboundRequests;
    }

    IntKeyMap<InboundRequest> getInboundRequests() {
        return inboundRequests;
    }

    AtomicBoolean getClosed() {
        return closed;
    }

    RemoteConnection getRemoteConnection() {
        return remoteConnection;
    }

    private static final ThreadLocal<RemoteConnectionHandler> current = new ThreadLocal<RemoteConnectionHandler>();

    static RemoteConnectionHandler getCurrent() {
        return current.get();
    }

    static RemoteConnectionHandler setCurrent(RemoteConnectionHandler newCurrent) {
        final ThreadLocal<RemoteConnectionHandler> current = RemoteConnectionHandler.current;
        try {
            return current.get();
        } finally {
            current.set(newCurrent);
        }
    }
}
