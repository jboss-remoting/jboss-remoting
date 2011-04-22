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
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.Result;
import org.xnio.channels.Channels;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.SuspendableWriteChannel;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemoteConnection {
    private final Pool<ByteBuffer> messageBufferPool;
    private final ConnectedMessageChannel channel;
    private final OptionMap optionMap;
    private volatile Result<ConnectionHandlerFactory> result;
    private final RemoteWriteListener listener = new RemoteWriteListener();

    RemoteConnection(final Pool<ByteBuffer> messageBufferPool, final ConnectedMessageChannel channel, final OptionMap map) {
        this.messageBufferPool = messageBufferPool;
        this.channel = channel;
        optionMap = map;
    }

    Pooled<ByteBuffer> allocate() {
        return messageBufferPool.allocate();
    }

    void setReadListener(ChannelListener<? super ConnectedMessageChannel> listener) {
        channel.getReadSetter().set(listener);
        if (listener != null) {
            channel.resumeReads();
        }
    }

    void setWriteListener(ChannelListener<? super ConnectedMessageChannel> listener) {
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
        RemoteLogger.log.connectionError(e);
        IoUtils.safeClose(channel);
        final Result<ConnectionHandlerFactory> result = this.result;
        if (result != null) {
            result.setException(e);
            this.result = null;
        }
    }

    void send(final Pooled<ByteBuffer> pooled) {
        listener.send(pooled);
    }

    OptionMap getOptionMap() {
        return optionMap;
    }

    ConnectedMessageChannel getChannel() {
        return channel;
    }

    final class RemoteWriteListener implements ChannelListener<ConnectedMessageChannel> {

        private final Queue<Pooled<ByteBuffer>> queue = new ArrayDeque<Pooled<ByteBuffer>>();

        RemoteWriteListener() {
        }

        public synchronized void handleEvent(final ConnectedMessageChannel channel) {
            assert channel == getChannel();
            Pooled<ByteBuffer> pooled;
            final Queue<Pooled<ByteBuffer>> queue = this.queue;
            try {
                while ((pooled = queue.peek()) != null) {
                    if (channel.send(pooled.getResource())) {
                        queue.poll().free();
                    } else {
                        // try again later
                        return;
                    }
                }
                if (channel.flush()) {
                    channel.suspendWrites();
                }
            } catch (IOException e) {
                handleException(e);
                while ((pooled = queue.poll()) != null) {
                    pooled.free();
                }
            }
            // else try again later
        }

        public synchronized void send(final Pooled<ByteBuffer> pooled) {
            final ConnectedMessageChannel channel = getChannel();
            boolean free = true;
            try {
                if (queue.isEmpty()) {
                    if (! channel.send(pooled.getResource())) {
                        queue.add(pooled);
                        free = false;
                        channel.resumeWrites();
                        return;
                    }
                    if (! channel.flush()) {
                        channel.resumeWrites();
                        return;
                    }
                } else {
                    queue.add(pooled);
                    free = false;
                }
            } catch (IOException e) {
                handleException(e);
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
