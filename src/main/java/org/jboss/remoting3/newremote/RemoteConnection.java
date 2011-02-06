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

package org.jboss.remoting3.newremote;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.Result;
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

    void send(final Pooled<ByteBuffer> pooled, final ChannelListener<? super ConnectedMessageChannel> nextListener) throws IOException {
        boolean ok = false;
        try {
            final ByteBuffer buffer = pooled.getResource();
            if (channel.send(buffer)) {
                if (channel.flush()) {
                    if (nextListener == null) {
                        channel.suspendWrites();
                    } else {
                        setWriteListener(nextListener);
                    }
                } else {
                    setWriteListener(new FlushListener<ConnectedMessageChannel>(nextListener));
                }
            } else {
                setWriteListener(new WriteListener<ConnectedMessageChannel>(nextListener, pooled));
            }
            ok = true;
        } finally {
            if (! ok) {
                pooled.free();
            }
        }
    }

    OptionMap getOptionMap() {
        return optionMap;
    }

    ConnectedMessageChannel getChannel() {
        return channel;
    }

    private class FlushListener<T extends SuspendableWriteChannel> implements ChannelListener<T> {

        private final ChannelListener<? super T> listener;

        FlushListener(final ChannelListener<? super T> listener) {
            this.listener = listener;
        }

        public void handleEvent(final T channel) {
            try {
                if (channel.flush()) {
                    channel.getWriteSetter().set(listener);
                }
            } catch (IOException e) {
                handleException(e);
            }
        }
    }

    private class WriteListener<T extends ConnectedMessageChannel> implements ChannelListener<T> {

        private final ChannelListener<? super ConnectedMessageChannel> listener;
        private final Pooled<ByteBuffer> pooled;

        WriteListener(final ChannelListener<? super ConnectedMessageChannel> listener, final Pooled<ByteBuffer> pooled) {
            this.listener = listener;
            this.pooled = pooled;
        }

        public void handleEvent(final T channel) {
            try {
                send(pooled, listener);
            } catch (IOException e) {
                handleException(e);
            }
        }
    }
}
