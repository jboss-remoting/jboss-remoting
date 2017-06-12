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

package org.jboss.remoting3.util;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

import org.jboss.remoting3.AbstractDelegatingMessageOutputStream;
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
        channel.addCloseHandler((closed, exception) -> connectionClosed(exception));
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

    /**
     * Allocate a message on behalf of an invocation, possibly blocking until a message is available.  The invocation
     * will automatically be removed if writing the message fails or is cancelled.
     *
     * @param invocation the invocation of the message
     * @return the allocated message
     * @throws IOException if an error occurs
     */
    public MessageOutputStream allocateMessage(Invocation invocation) throws IOException {
        return new AbstractDelegatingMessageOutputStream(allocateMessage()) {
            public MessageOutputStream cancel() {
                super.cancel();
                remove(invocation);
                return this;
            }
        };
    }

    private void connectionClosed(final IOException exception) {
        final Iterator<Invocation> iterator = invocations.iterator();
        while (iterator.hasNext()) {
            final Invocation invocation = iterator.next();
            try {
                if (exception != null) {
                    invocation.handleException(exception);
                } else {
                    invocation.handleClosed();
                }
            } catch (Throwable ignored) {
            }
            iterator.remove();
        }
    }
}
