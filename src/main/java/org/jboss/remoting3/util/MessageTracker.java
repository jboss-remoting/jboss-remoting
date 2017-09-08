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
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.remoting3.AbstractDelegatingMessageOutputStream;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageOutputStream;
import org.wildfly.common.Assert;

/**
 * An outbound message tracker, which can be used to easily avoid message overruns.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class MessageTracker {
    private final Channel channel;
    private final Object lock = new Object();
    private int counter;

    public MessageTracker(final Channel channel, final int limit) {
        Assert.checkNotNullParam("channel", channel);
        Assert.checkMinimumParameter("limit", 1, limit);
        this.channel = channel;
        counter = limit;
    }

    /**
     * Open a message, blocking if necessary.
     *
     * @return the message stream
     * @throws IOException if the channel failed to open the message
     * @throws InterruptedException if blocking was interrupted
     */
    public MessageOutputStream openMessage() throws IOException, InterruptedException {
        final Object lock = this.lock;
        int counter;
        synchronized (lock) {
            counter = this.counter;
            while (counter == 0) {
                lock.wait();
                counter = this.counter;
            }
            this.counter = counter - 1;
        }
        return getMessageInstance(channel.writeMessage());
    }

    /**
     * Open a message, blocking if necessary, and deferring any interrupts which occur while blocking.
     *
     * @return the message stream
     * @throws IOException if the channel failed to open the message
     */
    public MessageOutputStream openMessageUninterruptibly() throws IOException {
        final Object lock = this.lock;
        boolean intr = false;
        try {
            int counter;
            synchronized (lock) {
                counter = this.counter;
                while (counter == 0) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ex) {
                        intr = true;
                    }
                    counter = this.counter;
                }
                this.counter = counter - 1;
            }
            return getMessageInstance(channel.writeMessage());
        } finally {
            if (intr) Thread.currentThread().interrupt();
        }
    }

    private AbstractDelegatingMessageOutputStream getMessageInstance(final MessageOutputStream delegate) {
        return new AbstractDelegatingMessageOutputStream(delegate) {
            private final AtomicBoolean done = new AtomicBoolean();

            public void close() throws IOException {
                if (done.compareAndSet(false, true)) try {
                    super.close();
                } finally {
                    final Object lock = MessageTracker.this.lock;
                    synchronized (lock) {
                        MessageTracker.this.counter ++;
                        lock.notify();
                    }
                }
            }
        };
    }
}
