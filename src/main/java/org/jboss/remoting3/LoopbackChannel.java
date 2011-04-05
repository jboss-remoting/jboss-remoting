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

package org.jboss.remoting3;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.xnio.streams.Pipe;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class LoopbackChannel extends AbstractHandleableCloseable<Channel> implements Channel {
    private final Attachments attachments = new BasicAttachments();
    private final LoopbackChannel otherSide;
    private final Queue<Pipe> messageQueue;
    private final Object lock = new Object();
    private final int queueLength;
    private final int bufferSize;

    private MessageHandler messageHandler;

    private boolean closed;

    LoopbackChannel(final Executor executor, final LoopbackChannel otherSide) {
        super(executor);
        this.otherSide = otherSide;
        queueLength = 8;
        messageQueue = new ArrayDeque<Pipe>(queueLength);
        bufferSize = 8192;
    }

    LoopbackChannel(final Executor executor) {
        super(executor);
        otherSide = new LoopbackChannel(executor, this);
        queueLength = 8;
        messageQueue = new ArrayDeque<Pipe>(queueLength);
        bufferSize = 8192;
    }

    public OutputStream writeMessage() throws IOException {
        final LoopbackChannel otherSide = this.otherSide;
        final Queue<Pipe> otherSideQueue = otherSide.messageQueue;
        synchronized (otherSide.lock) {
            for (;;) {
                if (otherSide.closed) {
                    throw new NotOpenException("Writes have been shut down");
                }
                final int size = otherSideQueue.size();
                if (size == queueLength) {
                    try {
                        otherSide.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException();
                    }
                } else {
                    final Pipe pipe = new Pipe(bufferSize);
                    if (size == 0) {
                        final MessageHandler handler = otherSide.messageHandler;
                        if (handler != null) {
                            otherSide.messageHandler = null;
                            otherSide.notify();
                            executeMessageTask(pipe, handler);
                            return pipe.getOut();
                        }
                    }
                    otherSideQueue.add(pipe);
                    otherSide.notify();
                    return pipe.getOut();
                }
            }
        }
    }

    public void writeShutdown() throws IOException {
        final LoopbackChannel otherSide = this.otherSide;
        synchronized (otherSide.lock) {
            if (! otherSide.closed) {
                otherSide.closed = true;
                final MessageHandler messageHandler = otherSide.messageHandler;
                if (messageHandler != null && otherSide.messageQueue.isEmpty()) {
                    executeEndTask(messageHandler);
                } else {
                    otherSide.notify();
                }
            }
        }
    }

    public void receiveMessage(final MessageHandler handler) {
        final Object lock = this.lock;
        synchronized (lock) {
            if (messageHandler != null) {
                throw new IllegalStateException("Message handler already waiting");
            }
            if (closed) {
                executeEndTask(handler);
            } else {
                final Pipe pipe = messageQueue.poll();
                if (pipe != null) {
                    executeMessageTask(pipe, handler);
                } else {
                    messageHandler = handler;
                    lock.notify();
                }
            }
        }
    }

    private void executeEndTask(final MessageHandler handler) {
        getExecutor().execute(new Runnable() {
            public void run() {
                handler.handleEnd(LoopbackChannel.this);
            }
        });
    }

    private void executeMessageTask(final Pipe pipe, final MessageHandler handler) {
        getExecutor().execute(new Runnable() {
            public void run() {
                handler.handleMessage(otherSide, pipe.getIn());
            }
        });
    }

    public Attachments getAttachments() {
        return attachments;
    }

    protected void closeAction() throws IOException {
        synchronized (lock) {
            closed = true;
            lock.notifyAll();
        }
    }
}
