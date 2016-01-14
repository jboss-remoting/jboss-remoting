/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.jboss.logging.Logger;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.Result;
import org.xnio.XnioExecutor;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.SslChannel;
import org.xnio.sasl.SaslWrapper;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteConnection {

    static final Pooled<ByteBuffer> STARTTLS_SENTINEL = Buffers.emptyPooledByteBuffer();

    private static final String FQCN = RemoteConnection.class.getName();
    private final Pool<ByteBuffer> messageBufferPool;
    private final ConnectedMessageChannel channel;
    private final ConnectedStreamChannel underlyingChannel;
    private final OptionMap optionMap;
    private final RemoteWriteListener writeListener = new RemoteWriteListener();
    private final Executor executor;
    private final int heartbeatInterval;
    private volatile Result<ConnectionHandlerFactory> result;
    private volatile SaslWrapper saslWrapper;
    private final RemoteConnectionProvider remoteConnectionProvider;

    RemoteConnection(final Pool<ByteBuffer> messageBufferPool, final ConnectedStreamChannel underlyingChannel, final ConnectedMessageChannel channel, final OptionMap optionMap, final RemoteConnectionProvider remoteConnectionProvider) {
        this.messageBufferPool = messageBufferPool;
        this.underlyingChannel = underlyingChannel;
        this.channel = channel;
        this.optionMap = optionMap;
        heartbeatInterval = optionMap.get(RemotingOptions.HEARTBEAT_INTERVAL, RemotingOptions.DEFAULT_HEARTBEAT_INTERVAL);
        this.executor = remoteConnectionProvider.getExecutor();
        this.remoteConnectionProvider = remoteConnectionProvider;
    }

    Pooled<ByteBuffer> allocate() {
        return messageBufferPool.allocate();
    }

    void setReadListener(ChannelListener<? super ConnectedMessageChannel> listener, final boolean resume) {
        RemoteLogger.log.logf(RemoteConnection.class.getName(), Logger.Level.TRACE, null, "Setting read listener to %s", listener);
        channel.getReadSetter().set(listener);
        if (listener != null && resume) {
            channel.resumeReads();
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
        RemoteLogger.conn.logf(RemoteConnection.class.getName(), Logger.Level.TRACE, e, "Connection error detail");
        if (log) {
            RemoteLogger.conn.connectionError(e);
        }
        final XnioExecutor.Key key = writeListener.heartKey;
        if (key != null) {
            key.remove();
        }
        IoUtils.safeClose(channel);
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

    ConnectedMessageChannel getChannel() {
        return channel;
    }

    ChannelListener<ConnectedMessageChannel> getWriteListener() {
        return writeListener;
    }

    public Executor getExecutor() {
        return executor;
    }

    public SslChannel getSslChannel() {
        return underlyingChannel instanceof SslChannel ? (SslChannel) underlyingChannel : null;
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
            channel.close();
        } catch (IOException e) {
            RemoteLogger.conn.debug("Error closing remoting channel", e);
        }
    }

    void sendAlive() {
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
            channel.wakeupReads();
        } finally {
            if (! ok) pooled.free();
        }
    }

    void sendAliveResponse() {
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

    final class RemoteWriteListener implements ChannelListener<ConnectedMessageChannel> {

        private final Queue<Pooled<ByteBuffer>> queue = new ArrayDeque<Pooled<ByteBuffer>>();
        private XnioExecutor.Key heartKey;
        private boolean closed;

        RemoteWriteListener() {
        }

        public void handleEvent(final ConnectedMessageChannel channel) {
            synchronized (queue) {
                assert channel == getChannel();
                Pooled<ByteBuffer> pooled;
                final Queue<Pooled<ByteBuffer>> queue = this.queue;
                try {
                    while ((pooled = queue.peek()) != null) {
                        final ByteBuffer buffer = pooled.getResource();
                        if (buffer.hasRemaining()) {
                            if (channel.send(buffer)) {
                                RemoteLogger.conn.logf(FQCN, Logger.Level.TRACE, null, "Sent message %s (via queue)", buffer);
                                queue.poll().free();
                            } else {
                                // try again later
                                return;
                            }
                        } else {
                            if (pooled == STARTTLS_SENTINEL) {
                                if (channel.flush()) {
                                    final SslChannel sslChannel = getSslChannel();
                                    assert sslChannel != null; // because STARTTLS would be false in this case
                                    sslChannel.startHandshake();
                                } else {
                                    // try again later
                                    return;
                                }
                            }
                            // otherwise skip other empty message rather than try and write it
                            queue.poll().free();
                        }
                    }
                    if (channel.flush()) {
                        RemoteLogger.conn.logf(FQCN, Logger.Level.TRACE, null, "Flushed channel");
                        if (closed) {
                            terminateHeartbeat();
                            // End of queue reached; shut down and try to flush the remainder
                            channel.shutdownWrites();
                            if (channel.flush()) {
                                RemoteLogger.conn.logf(FQCN, Logger.Level.TRACE, null, "Shut down writes on channel");
                                return;
                            }
                            // either this is successful and no more notifications will come, or not and it will be retried
                            // either way we're done here
                            return;
                        } else {
                            this.heartKey = channel.getWriteThread().executeAfter(heartbeatCommand, heartbeatInterval, TimeUnit.MILLISECONDS);
                        }
                        channel.suspendWrites();
                    }
                } catch (IOException e) {
                    handleException(e, false);
                    channel.wakeupReads();
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
                final ConnectedMessageChannel channel = getChannel();
                try {
                    if (! queue.isEmpty()) {
                        channel.resumeWrites();
                        return;
                    }
                    channel.shutdownWrites();
                    if (! channel.flush()) {
                        channel.resumeWrites();
                        return;
                    }
                    RemoteLogger.conn.logf(FQCN, Logger.Level.TRACE, null, "Shut down writes on channel");
                } catch (IOException e) {
                    handleException(e, false);
                    channel.wakeupReads();
                    Pooled<ByteBuffer> unqueued;
                    while ((unqueued = queue.poll()) != null) {
                        unqueued.free();
                    }
                }
            }
        }

        public void send(final Pooled<ByteBuffer> pooled, final boolean close) {
            channel.getIoThread().execute(new Runnable() {
                @Override
                public void run() {

                    synchronized (queue) {
                        XnioExecutor.Key heartKey = RemoteWriteListener.this.heartKey;
                        if (heartKey != null) heartKey.remove();
                        if (closed) { pooled.free(); return; }
                        if (close) { closed = true; }
                        final ConnectedMessageChannel channel = getChannel();
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
                            final ByteBuffer buffer = pooled.getResource();
                            RemoteLogger.conn.logf(FQCN, Logger.Level.TRACE, null, "Can't directly send message %s, enqueued", buffer);
                            final boolean empty = queue.isEmpty();
                            queue.add(pooled);
                            free = false;
                            if (empty) {
                                channel.resumeWrites();
                            }
                        } catch (IOException e) {
                            handleException(e, false);
                            channel.wakeupReads();
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
                }
            });
        }
    }

    private final Runnable heartbeatCommand = new Runnable() {
        public void run() {
            sendAlive();
        }
    };

    public String toString() {
        return String.format("Remoting connection %08x to %s of %s", Integer.valueOf(hashCode()), channel.getPeerAddress(), getRemoteConnectionProvider().getConnectionProviderContext().getEndpoint());
    }
}
