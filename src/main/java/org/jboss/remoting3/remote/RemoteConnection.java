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
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.Result;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.SslChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteConnection {
    private final Pool<ByteBuffer> messageBufferPool;
    private final ConnectedMessageChannel channel;
    private final ConnectedStreamChannel underlyingChannel;
    private final OptionMap optionMap;
    private final RemoteWriteListener writeListener = new RemoteWriteListener();
    private final Executor executor;
    private volatile Result<ConnectionHandlerFactory> result;
    private final AtomicBoolean closeSent = new AtomicBoolean(false);

    RemoteConnection(final Pool<ByteBuffer> messageBufferPool, final ConnectedStreamChannel underlyingChannel, final ConnectedMessageChannel channel, final OptionMap optionMap, final Executor executor) {
        this.messageBufferPool = messageBufferPool;
        this.underlyingChannel = underlyingChannel;
        this.channel = channel;
        this.optionMap = optionMap;
        this.executor = executor;
    }

    Pooled<ByteBuffer> allocate() {
        return messageBufferPool.allocate();
    }

    void setReadListener(ChannelListener<? super ConnectedMessageChannel> listener) {
        RemoteLogger.log.tracef("Setting read listener to %s", listener);
        channel.getReadSetter().set(listener);
        if (listener != null) {
            channel.resumeReads();
        }
    }

    void setWriteListener(ChannelListener<? super ConnectedMessageChannel> listener) {
        RemoteLogger.log.tracef("Setting write listener to %s", listener);
        channel.getWriteSetter().set(listener);
        if (listener != null) {
            channel.resumeWrites();
        }
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
        if (log) {
            RemoteLogger.log.connectionError(e);
        } else {
            RemoteLogger.log.tracef(e, "Unlogworthy connection error");
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

    void handleIncomingCloseRequest() {
        RemoteLogger.log.tracef("Received connection close request");
        try {
            channel.shutdownReads();
        } catch (IOException e) {
            RemoteLogger.log.debugf("Failed to shut down reads: %s", e);
        }
        sendCloseRequest();
    }

    boolean handleOutboundCloseRequest() {
        RemoteLogger.log.trace("Initiating connection close request");
        return sendCloseRequest();
    }

    void handleChannelClose() {
        closeSent.set(true);
    }

    private boolean sendCloseRequest() {
        if (closeSent.compareAndSet(false, true)) {
            final Pooled<ByteBuffer> pooled = allocate();
            boolean ok = false;
            try {
                final ByteBuffer buffer = pooled.getResource();
                buffer.put(Protocol.CONNECTION_CLOSE);
                buffer.flip();
                writeListener.send(pooled, true);
                ok = true;
            } finally {
                if (! ok) {
                    pooled.free();
                }
            }
            return true;
        } else {
            return false;
        }
    }

    final class RemoteWriteListener implements ChannelListener<ConnectedMessageChannel> {

        private final Queue<Pooled<ByteBuffer>> queue = new ArrayDeque<Pooled<ByteBuffer>>();
        private boolean closed;

        RemoteWriteListener() {
        }

        public synchronized void handleEvent(final ConnectedMessageChannel channel) {
            assert channel == getChannel();
            Pooled<ByteBuffer> pooled;
            final Queue<Pooled<ByteBuffer>> queue = this.queue;
            try {
                while ((pooled = queue.peek()) != null) {
                    final ByteBuffer buffer = pooled.getResource();
                    if (channel.send(buffer)) {
                        RemoteLogger.log.tracef("Sent message %s (via queue)", buffer);
                        queue.poll().free();
                    } else {
                        // try again later
                        channel.suspendWrites();
                        return;
                    }
                }
                if (channel.flush()) {
                    RemoteLogger.log.tracef("Flushed channel");
                    if (closed) {
                        // either this is successful and no more notifications will come, or not and it will be retried
                        channel.shutdownWrites();
                        // either way we're done here
                        return;
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

        public synchronized void shutdownWrites() {
            closed = true;
            final ConnectedMessageChannel channel = getChannel();
            try {
                if (queue.isEmpty()) {
                    if (! channel.flush()) {
                        channel.resumeWrites();
                        return;
                    }
                    RemoteLogger.log.tracef("Flushed channel");
                }
            } catch (IOException e) {
                handleException(e, false);
                Pooled<ByteBuffer> unqueued;
                while ((unqueued = queue.poll()) != null) {
                    unqueued.free();
                }
            }
        }

        public synchronized void send(final Pooled<ByteBuffer> pooled, final boolean close) {
            if (closed) { pooled.free(); return; }
            if (close) { closed = true; }
            final ConnectedMessageChannel channel = getChannel();
            boolean free = true;
            try {
                if (queue.isEmpty()) {
                    final ByteBuffer buffer = pooled.getResource();
                    if (! channel.send(buffer)) {
                        RemoteLogger.log.tracef("Can't directly send message %s, enqueued", buffer);
                        queue.add(pooled);
                        free = false;
                        channel.resumeWrites();
                        return;
                    }
                    RemoteLogger.log.tracef("Sent message %s (direct)", buffer);
                    if (! channel.flush()) {
                        channel.resumeWrites();
                        return;
                    }
                    RemoteLogger.log.tracef("Flushed channel");
                    if (close) {
                        if (channel.shutdownWrites()) {
                            RemoteLogger.log.trace("Shut down writes on channel");
                        } else {
                            channel.resumeWrites();
                            return;
                        }
                    }
                } else {
                    queue.add(pooled);
                    free = false;
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
    }
}
