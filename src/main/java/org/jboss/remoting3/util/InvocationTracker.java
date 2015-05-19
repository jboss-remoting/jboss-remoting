/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.jboss.remoting3.util;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.remoting3.Channel;
import org.jboss.remoting3.ChannelClosedException;
import org.jboss.remoting3.MessageInputStream;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3._private.IntIndexHashMap;
import org.jboss.remoting3._private.IntIndexMap;
import org.wildfly.common.Assert;

/**
 * An invocation tracker.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class InvocationTracker {
    private final IntIndexMap<Invocation> invocations = new IntIndexHashMap<Invocation>(Invocation::getIndex);
    private final Channel channel;
    private final int maxMessages;
    private final AtomicInteger msgFree;

    private InvocationTracker(final Channel channel, final int maxMessages, int dummy) {
        Assert.checkMinimumParameter("maxMessages", 1, maxMessages);
        this.channel = channel;
        this.maxMessages = maxMessages;
        channel.addCloseHandler((closed, exception) -> connectionClosed());
        msgFree = new AtomicInteger(this.maxMessages);
    }

    /**
     * Construct a new instance.
     *
     * @param channel the channel that is being tracked
     * @param maxMessages the maximum number of concurrent messages to allow
     */
    public InvocationTracker(final Channel channel, final int maxMessages) {
        this(Assert.checkNotNullParam("channel", channel), maxMessages, 0);
    }

    /**
     * Construct a new instance, using the maximum number of messages from the channel.
     *
     * @param channel the channel that is being tracked
     */
    public InvocationTracker(final Channel channel) {
        this(Assert.checkNotNullParam("channel", channel), channel.getOption(RemotingOptions.MAX_OUTBOUND_MESSAGES).intValue(), 0);
    }

    /**
     * Determine if the tracker contains an entry at the given index.
     *
     * @param index the index
     * @return {@code true} if the tracker contains the entry, {@code false} otherwise
     */
    public boolean containsIndex(int index) {
        return invocations.containsKey(index);
    }

    /**
     * Put an invocation into the tracker if there is none with the corresponding ID.
     *
     * @param invocation the invocation
     * @return the existing invocation, or {@code null} if the put was successful
     */
    public Invocation putIfAbsent(Invocation invocation) {
        return invocations.putIfAbsent(invocation);
    }

    /**
     * Signal the arrival of a response with the given index.
     *
     * @param index the index of the response
     * @param parameter an integer parameter to pass to the response handler (typically a message ID type)
     * @param responseStream the response stream
     * @param remove {@code true} to release the index for subsequent invocations, {@code false} otherwise
     * @return {@code true} if the index was valid, {@code false} otherwise
     */
    public boolean signalResponse(int index, int parameter, MessageInputStream responseStream, boolean remove) {
        final Invocation invocation = remove ? invocations.removeKey(index) : invocations.get(index);
        if (invocation == null) {
            return false;
        }
        invocation.handleResponse(parameter, responseStream);
        return true;
    }

    /**
     * Allocate a message, possibly blocking until one is available.
     *
     * @return the allocated message
     * @throws IOException if an error occurs
     */
    public MessageOutputStream allocateMessage() throws IOException {
        int oldCnt;
        final AtomicInteger msgFree = this.msgFree;
        do {
            while ((oldCnt = msgFree.get()) == 0) {
                try {
                    msgFree.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Message allocation interrupted");
                }
            }
            if (oldCnt < 0) {
                throw new ChannelClosedException("Channel was closed");
            }
        } while (! msgFree.compareAndSet(oldCnt, oldCnt - 1));
        final MessageOutputStream message = channel.writeMessage();
        return new MessageOutputStream() {
            private final AtomicBoolean closed = new AtomicBoolean();

            public void flush() throws IOException {
                message.flush();
            }

            public void close() throws IOException {
                try {
                    message.close();
                } finally {
                    if (closed.compareAndSet(false, true) && msgFree.getAndIncrement() == 0) {
                        synchronized (msgFree) {
                            msgFree.notify();
                        }
                    }
                }
            }

            public MessageOutputStream cancel() {
                try {
                    message.cancel();
                } finally {
                    if (closed.compareAndSet(false, true) && msgFree.getAndIncrement() == 0) {
                        synchronized (msgFree) {
                            msgFree.notify();
                        }
                    }
                }
                return this;
            }

            public void writeUTF(final String s) throws IOException {
                message.writeUTF(s);
            }

            public void writeChars(final String s) throws IOException {
                message.writeChars(s);
            }

            public void writeBytes(final String s) throws IOException {
                message.writeBytes(s);
            }

            public void writeDouble(final double v) throws IOException {
                message.writeDouble(v);
            }

            public void writeFloat(final float v) throws IOException {
                message.writeFloat(v);
            }

            public void writeLong(final long v) throws IOException {
                message.writeLong(v);
            }

            public void writeInt(final int v) throws IOException {
                message.writeInt(v);
            }

            public void writeChar(final int v) throws IOException {
                message.writeChar(v);
            }

            public void writeShort(final int v) throws IOException {
                message.writeShort(v);
            }

            public void writeByte(final int v) throws IOException {
                message.writeByte(v);
            }

            public void writeBoolean(final boolean v) throws IOException {
                message.writeBoolean(v);
            }

            public void write(final int b) throws IOException {
                message.write(b);
            }
        };
    }

    private void connectionClosed() {
        synchronized (msgFree) {
            msgFree.set(Integer.MIN_VALUE);
            msgFree.notifyAll();
        }
        final Iterator<Invocation> iterator = invocations.iterator();
        while (iterator.hasNext()) {
            final Invocation invocation = iterator.next();
            try {
                invocation.handleClosed();
            } catch (Throwable ignored) {
            }
            iterator.remove();
        }
    }
}
