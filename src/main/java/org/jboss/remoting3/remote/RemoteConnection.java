/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3.remote;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3._private.Messages;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.xnio.Buffers;
import org.xnio.ByteBufferPool;
import org.xnio.ChannelListener;
import org.xnio.Connection;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pooled;
import org.xnio.Result;
import org.xnio.StreamConnection;
import org.xnio.XnioExecutor;
import org.xnio.channels.SslChannel;
import org.xnio.conduits.ConduitStreamSinkChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.sasl.SaslWrapper;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteConnection {

    static final Pooled<ByteBuffer> STARTTLS_SENTINEL = Buffers.emptyPooledByteBuffer();

    private static final String FQCN = RemoteConnection.class.getName();
    private final StreamConnection connection;
    private final MessageReader messageReader;
    private final SslChannel sslChannel;
    private final OptionMap optionMap;
    private final RemoteWriteListener writeListener = new RemoteWriteListener();
    private final Executor executor;
    private final int heartbeatInterval;
    private volatile Result<ConnectionHandlerFactory> result;
    private volatile SaslWrapper saslWrapper;
    private volatile SecurityIdentity identity;
    private final RemoteConnectionProvider remoteConnectionProvider;

    RemoteConnection(final StreamConnection connection, final SslChannel sslChannel, final OptionMap optionMap, final RemoteConnectionProvider remoteConnectionProvider) {
        this.connection = connection;
        this.messageReader = new MessageReader(connection.getSourceChannel(), writeListener.queue);
        this.sslChannel = sslChannel;
        this.optionMap = optionMap;
        heartbeatInterval = optionMap.get(RemotingOptions.HEARTBEAT_INTERVAL, RemotingOptions.DEFAULT_HEARTBEAT_INTERVAL);
        Messages.conn.tracef("Initialized connection from %s to %s with options %s", connection.getPeerAddress(), connection.getLocalAddress(), optionMap);
        this.executor = remoteConnectionProvider.getExecutor();
        this.remoteConnectionProvider = remoteConnectionProvider;
    }

    Pooled<ByteBuffer> allocate() {
        return Buffers.globalPooledWrapper(ByteBufferPool.MEDIUM_DIRECT.allocate());
    }

    void setReadListener(ChannelListener<ConduitStreamSourceChannel> listener, final boolean resume) {
        Messages.log.logf(RemoteConnection.class.getName(), Logger.Level.TRACE, null, "Setting read listener to %s", listener);
        messageReader.setReadListener(listener);
        if (listener != null && resume) {
            messageReader.resumeReads();
        }
    }

    RemoteConnectionProvider getRemoteConnectionProvider() {
        return remoteConnectionProvider;
    }

    Result<ConnectionHandlerFactory> getResult() {
        return result;
    }

    void setResult(final Result<ConnectionHandlerFactory> result) {
        this.result = result;
    }

    void handleException(IOException e) {
        handleException(e, true);
    }

    void handleException(IOException e, boolean log) {
        Messages.conn.logf(RemoteConnection.class.getName(), Logger.Level.TRACE, e, "Connection error detail");
        if (log) {
            Messages.conn.connectionError(e);
        }
        final XnioExecutor.Key key = writeListener.heartKey;
        if (key != null) {
            key.remove();
        }
        synchronized (getLock()) {
            IoUtils.safeClose(connection);
        }
        final Result<ConnectionHandlerFactory> result = this.result;
        if (result != null) {
            result.setException(e);
            this.result = null;
        }
    }

    void send(final Pooled<ByteBuffer> pooled) {
        writeListener.send(pooled, false);
    }

    void send(final Pooled<ByteBuffer> pooled, boolean close) {
        writeListener.send(pooled, close);
    }

    void shutdownWrites() {
        writeListener.shutdownWrites();
    }

    OptionMap getOptionMap() {
        return optionMap;
    }

    MessageReader getMessageReader() {
        return messageReader;
    }

    RemoteWriteListener getWriteListener() {
        return writeListener;
    }

    public Executor getExecutor() {
        return executor;
    }

    public SslChannel getSslChannel() {
        return sslChannel;
    }

    SaslWrapper getSaslWrapper() {
        return saslWrapper;
    }

    void setSaslWrapper(final SaslWrapper saslWrapper) {
        this.saslWrapper = saslWrapper;
    }

    void handlePreAuthCloseRequest() {
        try {
            terminateHeartbeat();
            synchronized (getLock()) {
                connection.close();
            }
        } catch (IOException e) {
            Messages.conn.debug("Error closing remoting channel", e);
        }
    }

    void sendAlive() {
        Messages.conn.trace("Sending connection alive");
        final Pooled<ByteBuffer> pooled = allocate();
        boolean ok = false;
        try {
            final ByteBuffer buffer = pooled.getResource();
            buffer.put(Protocol.CONNECTION_ALIVE);
            buffer.limit(80);
            Buffers.addRandom(buffer);
            buffer.flip();
            send(pooled);
            ok = true;
            messageReader.wakeupReads();
        } finally {
            if (! ok) pooled.free();
        }
    }

    void sendAliveResponse() {
        Messages.conn.trace("Sending connection alive ack");
        final Pooled<ByteBuffer> pooled = allocate();
        boolean ok = false;
        try {
            final ByteBuffer buffer = pooled.getResource();
            buffer.put(Protocol.CONNECTION_ALIVE_ACK);
            buffer.limit(80);
            Buffers.addRandom(buffer);
            buffer.flip();
            send(pooled);
            ok = true;
        } finally {
            if (! ok) pooled.free();
        }
    }

    void terminateHeartbeat() {
        final XnioExecutor.Key key = writeListener.heartKey;
        if (key != null) {
            key.remove();
        }
    }

    Object getLock() {
        return writeListener.queue;
    }

    SecurityIdentity getIdentity() {
        return identity;
    }

    void setIdentity(final SecurityIdentity identity) {
        this.identity = identity;
    }

    InetSocketAddress getPeerAddress() {
        return connection.getPeerAddress(InetSocketAddress.class);
    }

    InetSocketAddress getLocalAddress() {
        return connection.getLocalAddress(InetSocketAddress.class);
    }

    Connection getConnection() {
        return connection;
    }

    final class RemoteWriteListener implements ChannelListener<ConduitStreamSinkChannel> {

        private final Queue<Pooled<ByteBuffer>> queue = new ArrayDeque<Pooled<ByteBuffer>>();
        private volatile XnioExecutor.Key heartKey;
        private boolean closed;
        private ByteBuffer headerBuffer = ByteBuffer.allocateDirect(4);
        private final ByteBuffer[] cachedArray = new ByteBuffer[] { headerBuffer, null };
        private volatile long expireTime = -1;

        RemoteWriteListener() {
        }

        public void handleEvent(final ConduitStreamSinkChannel channel) {
            final ByteBuffer[] cachedArray = this.cachedArray;
            synchronized (queue) {
                Pooled<ByteBuffer> pooled;
                final Queue<Pooled<ByteBuffer>> queue = this.queue;
                try {
                    ByteBuffer buffer = cachedArray[1];
                    if (buffer != null) {
                        channel.write(cachedArray);
                        if (buffer.hasRemaining()) {
                            return;
                        }
                    }
                    cachedArray[1] = null;
                    while ((pooled = queue.peek()) != null) {
                        buffer = pooled.getResource();
                        if (buffer.hasRemaining()) { // no empty messages
                            headerBuffer.putInt(0, buffer.remaining());
                            headerBuffer.position(0);
                            cachedArray[1] = buffer;
                            final long res = channel.write(cachedArray);
                            Messages.conn.tracef("Sent %d bytes", res);
                            if (buffer.hasRemaining()) {
                                // try again later
                                return;
                            } else {
                                cachedArray[1] = null;
                                queue.poll().free();
                            }
                        } else {
                            if (pooled == STARTTLS_SENTINEL) {
                                if (channel.flush()) {
                                    Messages.conn.trace("Flushed channel");
                                    final SslChannel sslChannel = getSslChannel();
                                    assert sslChannel != null; // because STARTTLS would be false in this case
                                    sslChannel.startHandshake();
                                } else {
                                    // try again later
                                    Messages.conn.trace("Flush stalled");
                                    return;
                                }
                            }
                            // otherwise skip other empty message rather than try and write it
                            queue.poll().free();
                        }
                    }
                    if (channel.flush()) {
                        Messages.conn.trace("Flushed channel");
                        if (closed) {
                            terminateHeartbeat();
                            // End of queue reached; shut down and try to flush the remainder
                            channel.shutdownWrites();
                            if (channel.flush()) {
                                Messages.conn.trace("Shut down writes on channel");
                                return;
                            }
                            // either this is successful and no more notifications will come, or not and it will be retried
                            // either way we're done here
                            return;
                        } else {
                            if (heartbeatInterval != 0) {
                                this.expireTime = System.currentTimeMillis() + heartbeatInterval;
                                 if (this.heartKey == null) {
                                     final XnioExecutor executor = channel.getWriteThread();
                                     final Runnable heartBeat = new HeartBeat(executor);
                                     this.heartKey = executor.executeAfter(heartBeat, heartbeatInterval, TimeUnit.MILLISECONDS);
                                 }
                            }
                        }
                        channel.suspendWrites();
                    }
                } catch (IOException e) {
                    handleException(e, false);
                    while ((pooled = queue.poll()) != null) {
                        pooled.free();
                    }
                }
                // else try again later
            }
        }

        public void shutdownWrites() {
            synchronized (queue) {
                closed = true;
                terminateHeartbeat();
                final ConduitStreamSinkChannel sinkChannel = connection.getSinkChannel();
                try {
                    if (! queue.isEmpty()) {
                        sinkChannel.resumeWrites();
                        return;
                    }
                    sinkChannel.shutdownWrites();
                    if (! sinkChannel.flush()) {
                        sinkChannel.resumeWrites();
                        return;
                    }
                    Messages.conn.logf(FQCN, Logger.Level.TRACE, null, "Shut down writes on channel");
                } catch (IOException e) {
                    handleException(e, false);
                    Pooled<ByteBuffer> unqueued;
                    while ((unqueued = queue.poll()) != null) {
                        unqueued.free();
                    }
                }
            }
        }

        public void send(final Pooled<ByteBuffer> pooled, final boolean close) {
            connection.getIoThread().execute(() -> {
                synchronized (queue) {
                    XnioExecutor.Key heartKey1 = heartKey;
                    if (heartKey1 != null)
                        this.expireTime = System.currentTimeMillis() + heartbeatInterval;
                    if (closed) { pooled.free(); return; }
                    if (close) { closed = true; }
                    boolean free = true;
                    try {
                        final SaslWrapper wrapper = saslWrapper;
                        if (wrapper != null) {
                            final ByteBuffer buffer = pooled.getResource();
                            final ByteBuffer source = buffer.duplicate();
                            buffer.clear();
                            wrapper.wrap(buffer, source);
                            buffer.flip();
                        }
                        final boolean empty = queue.isEmpty();
                        queue.add(pooled);
                        free = false;
                        if (empty) {
                            //if there was no data previously queued we add a task to attempt to write the
                            //data, and resume writes if it fails. This means that if we have multiple messages
                            //that are to be send they can all be batched into a single write, while also
                            //preventing a resumeWrites unless it is actually required
                            connection.getIoThread().execute(flushTask);
                        }
                    } catch (IOException e) {
                        handleException(e, false);
                        Pooled<ByteBuffer> unqueued;
                        while ((unqueued = queue.poll()) != null) {
                            unqueued.free();
                        }
                    } finally {
                        if (free) {
                            pooled.free();
                        }
                    }
                }
            });
        }

        private class HeartBeat implements Runnable {

            private final XnioExecutor executor;

            public HeartBeat(XnioExecutor executor) {
                this.executor = executor;
            }

            public void run() {
                long currentTime = System.currentTimeMillis();
                if (currentTime >= expireTime) {
                    sendAlive();
                    heartKey = executor.executeAfter(this, heartbeatInterval, TimeUnit.MILLISECONDS);
                } else {
                    final long nextBeatInterval = expireTime - System.currentTimeMillis();
                    // prevent negative intervals by scheduling to run immediately if nextBeatInterval happens to be negative
                    heartKey = executor.executeAfter(this, nextBeatInterval < 0? 0: nextBeatInterval, TimeUnit.MILLISECONDS);
                }

            }
        }

        private final Runnable flushTask = new Runnable() {
            @Override
            public void run() {
                handleEvent(RemoteConnection.this.connection.getSinkChannel());
                if(!queue.isEmpty()) {
                    connection.getSinkChannel().resumeWrites();
                }
            }
        };
    }

    public String toString() {
        return String.format("Remoting connection %08x to %s of %s", Integer.valueOf(hashCode()), connection.getPeerAddress(), getRemoteConnectionProvider().getConnectionProviderContext().getEndpoint());
    }
}
