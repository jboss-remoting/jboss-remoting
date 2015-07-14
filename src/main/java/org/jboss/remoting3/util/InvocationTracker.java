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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

import org.jboss.remoting3.Channel;
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
    private final MessageTracker messageTracker;
    private final IntUnaryOperator intMasker;

    /**
     * Construct a new instance.
     *
     * @param channel the channel that is being tracked
     * @param maxMessages the maximum number of concurrent messages to allow
     * @param intMasker the function to apply to ID numbers to limit them to a specific range
     */
    public InvocationTracker(final Channel channel, final int maxMessages, final IntUnaryOperator intMasker) {
        this(channel, new MessageTracker(channel, maxMessages), intMasker);
    }

    /**
     * Construct a new instance.
     *
     * @param channel the channel that is being tracked
     * @param messageTracker the message tracker to use
     * @param intMasker the function to apply to ID numbers to limit them to a specific range
     */
    public InvocationTracker(final Channel channel, final MessageTracker messageTracker, final IntUnaryOperator intMasker) {
        Assert.checkNotNullParam("channel", channel);
        Assert.checkNotNullParam("messageTracker", messageTracker);
        Assert.checkNotNullParam("intMasker", intMasker);
        this.messageTracker = messageTracker;
        channel.addCloseHandler((closed, exception) -> connectionClosed());
        this.intMasker = intMasker;
    }

    /**
     * Construct a new instance, using the maximum number of messages from the channel.
     *
     * @param channel the channel that is being tracked
     * @param intMasker the function to apply to ID numbers to limit them to a specific range
     */
    public InvocationTracker(final Channel channel, final IntUnaryOperator intMasker) {
        this(channel, channel.getOption(RemotingOptions.MAX_OUTBOUND_MESSAGES).intValue(), intMasker);
    }

    /**
     * Construct a new instance.
     *
     * @param channel the channel that is being tracked
     * @param maxMessages the maximum number of concurrent messages to allow
     */
    public InvocationTracker(final Channel channel, final int maxMessages) {
        this(channel, maxMessages, InvocationTracker::defaultFunction);
    }

    /**
     * Construct a new instance, using the maximum number of messages from the channel.
     *
     * @param channel the channel that is being tracked
     */
    public InvocationTracker(final Channel channel) {
        this(channel, channel.getOption(RemotingOptions.MAX_OUTBOUND_MESSAGES).intValue(), InvocationTracker::defaultFunction);
    }

    private static int defaultFunction(int random) {
        return random & 0xffff;
    }

    /**
     * Add an invocation to this tracker.
     *
     * @param producer the invocation producer, which may be called more than once
     * @param <T> the invocation type
     * @return the produced invocation
     */
    public <T extends Invocation> T addInvocation(IntFunction<T> producer) {
        final ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        final IntUnaryOperator intMasker = this.intMasker;
        final IntIndexMap<Invocation> invocations = this.invocations;
        int id;
        T invocation;
        for (;;) {
            id = intMasker.applyAsInt(threadLocalRandom.nextInt());
            if (invocations.containsKey(id)) {
                continue;
            }
            invocation = producer.apply(id);
            if (invocations.putIfAbsent(invocation) != null) {
                continue;
            }
            return invocation;
        }
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
     * Unconditionally remove an invocation from the map.  This should only be done if the outbound request definitely
     * failed to be written.
     *
     * @param invocation the invocation
     */
    public void remove(final Invocation invocation) {
        invocations.remove(invocation);
    }

    /**
     * Allocate a message, possibly blocking until one is available.
     *
     * @return the allocated message
     * @throws IOException if an error occurs
     */
    public MessageOutputStream allocateMessage() throws IOException {
        try {
            return messageTracker.openMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Message allocation interrupted");
        }
    }

    private void connectionClosed() {
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
