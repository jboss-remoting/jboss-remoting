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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.remoting3.AbstractDelegatingMessageOutputStream;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.MessageOutputStream;

/**
 * An outbound message tracker, which can be used to easily avoid message overruns.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class MessageTracker {
    private final Channel channel;
    private final int limit;
    private final AtomicInteger counter;

    public MessageTracker(final Channel channel, final int limit) {
        this.channel = channel;
        this.limit = limit;
        counter = new AtomicInteger(limit);
    }

    /**
     * Open a message, blocking if necessary.
     *
     * @return the message stream
     * @throws IOException if the channel failed to open the message
     * @throws InterruptedException if blocking was interrupted
     */
    public MessageOutputStream openMessage() throws IOException, InterruptedException {
        int oldVal;
        do {
            oldVal = counter.get();
            if (oldVal == 0) {
                // try with lock
                synchronized (counter) {
                    oldVal = counter.get();
                    while (oldVal == 0) {
                        counter.wait();
                    }
                }
            }
        } while (! counter.compareAndSet(oldVal, oldVal - 1));
        return getMessageInstance(channel.writeMessage());
    }

    /**
     * Open a message, blocking if necessary, and deferring any interrupts which occur while blocking.
     *
     * @return the message stream
     * @throws IOException if the channel failed to open the message
     */
    public MessageOutputStream openMessageUninterruptibly() throws IOException {
        boolean intr = false;
        try {
            int oldVal;
            do {
                oldVal = counter.get();
                if (oldVal == 0) {
                    // try with lock
                    synchronized (counter) {
                        oldVal = counter.get();
                        while (oldVal == 0) try {
                            counter.wait();
                        } catch (InterruptedException e) {
                            intr = true;
                        }
                    }
                }
            } while (! counter.compareAndSet(oldVal, oldVal - 1));
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
                    int oldVal;
                    do {
                        oldVal = counter.get();
                    } while (! counter.compareAndSet(oldVal, oldVal + 1));
                    if (oldVal == limit - 1) {
                        synchronized (counter) {
                            // release one
                            counter.notify();
                        }
                    }
                }
            }
        };
    }
}
