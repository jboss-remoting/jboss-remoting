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
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.xnio.IoUtils;
import org.xnio.streams.Pipe;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class LocalChannel extends AbstractHandleableCloseable<Channel> implements Channel {
    private final Attachments attachments = new Attachments();
    private final LocalChannel otherSide;
    private final ConnectionHandlerContext connectionHandlerContext;
    private final Queue<In> messageQueue;
    private final Object lock = new Object();
    private final int queueLength;
    private final int bufferSize;

    private Receiver messageHandler;

    private boolean closed;

    LocalChannel(final Executor executor, final LocalChannel otherSide, final ConnectionHandlerContext connectionHandlerContext) {
        super(executor, true);
        this.otherSide = otherSide;
        this.connectionHandlerContext = connectionHandlerContext;
        queueLength = 8;
        messageQueue = new ArrayDeque<In>(queueLength);
        bufferSize = 8192;
    }

    LocalChannel(final Executor executor, final ConnectionHandlerContext connectionHandlerContext) {
        super(executor, true);
        this.connectionHandlerContext = connectionHandlerContext;
        otherSide = new LocalChannel(executor, this, connectionHandlerContext);
        queueLength = 8;
        messageQueue = new ArrayDeque<In>(queueLength);
        bufferSize = 8192;
    }

    public MessageOutputStream writeMessage() throws IOException {
        final LocalChannel otherSide = this.otherSide;
        final Queue<In> otherSideQueue = otherSide.messageQueue;
        synchronized (otherSide.lock) {
            for (;;) {
                if (otherSide.closed) {
                    throw new NotOpenException("Writes have been shut down");
                }
                final int size = otherSideQueue.size();
                if (size == queueLength) {
                    try {
                        otherSide.lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException();
                    }
                } else {
                    final Pipe pipe = new Pipe(bufferSize);
                    In in = new In(pipe.getIn());
                    if (size == 0) {
                        final Receiver handler = otherSide.messageHandler;
                        if (handler != null) {
                            otherSide.messageHandler = null;
                            otherSide.lock.notify();
                            executeMessageTask(handler, in);
                            return new Out(pipe.getOut(), in);
                        }
                    }
                    otherSideQueue.add(in);
                    otherSide.lock.notify();
                    return new Out(pipe.getOut(), in);
                }
            }
        }
    }

    public void writeShutdown() throws IOException {
        final LocalChannel otherSide = this.otherSide;
        synchronized (otherSide.lock) {
            if (! otherSide.closed) {
                otherSide.closed = true;
                final Receiver messageHandler = otherSide.messageHandler;
                if (messageHandler != null && otherSide.messageQueue.isEmpty()) {
                    executeEndTask(messageHandler);
                } else {
                    otherSide.lock.notify();
                }
            }
        }
    }

    public void receiveMessage(final Receiver handler) {
        final Object lock = this.lock;
        synchronized (lock) {
            if (messageHandler != null) {
                throw new IllegalStateException("Message handler already waiting");
            }
            if (closed) {
                executeEndTask(handler);
            } else {
                final In in = messageQueue.poll();
                if (in != null) {
                    executeMessageTask(handler, in);
                } else {
                    messageHandler = handler;
                    lock.notify();
                }
            }
        }
    }

    private void executeEndTask(final Receiver handler) {
        getExecutor().execute(new Runnable() {
            public void run() {
                handler.handleEnd(LocalChannel.this);
            }
        });
    }

    private void executeMessageTask(final Receiver handler, final In in) {
        getExecutor().execute(new Runnable() {
            public void run() {
                handler.handleMessage(LocalChannel.this, in);
            }
        });
    }

    public Attachments getAttachments() {
        return attachments;
    }

    public Connection getConnection() {
        return connectionHandlerContext.getConnection();
    }

    protected void closeAction() throws IOException {
        synchronized (lock) {
            closed = true;
            lock.notifyAll();
        }
        otherSide.connectionHandlerContext.remoteClosed();
        closeComplete();
    }

    LocalChannel getOtherSide() {
        return otherSide;
    }

    static final class Out extends MessageOutputStream {
        private final OutputStream outputStream;
        private final In in;

        Out(final OutputStream outputStream, final In in) {
            this.outputStream = outputStream;
            this.in = in;
        }

        public void flush() throws IOException {
            outputStream.flush();
        }

        public void close() throws IOException {
            outputStream.close();
        }

        public void write(final int b) throws IOException {
            outputStream.write(b);
        }

        public void write(final byte[] b) throws IOException {
            outputStream.write(b);
        }

        public void write(final byte[] b, final int off, final int len) throws IOException {
            outputStream.write(b, off, len);
        }

        public Out cancel() {
            in.doCancel();
            IoUtils.safeClose(outputStream);
            return this;
        }
    }

    static final class In extends MessageInputStream {
        private final InputStream inputStream;
        private volatile boolean cancelled;

        In(final InputStream inputStream) {
            this.inputStream = inputStream;
        }

        public InputStream getInputStream() throws IOException {
            return inputStream;
        }

        synchronized void doCancel() {
            cancelled = true;
        }

        public synchronized boolean wasCancelled() {
            return cancelled;
        }

        public int read() throws IOException {
            checkCancel();
            return inputStream.read();
        }

        private synchronized void checkCancel() throws MessageCancelledException {
            if (cancelled) {
                throw new MessageCancelledException();
            }
        }

        public int read(final byte[] b) throws IOException {
            checkCancel();
            return inputStream.read(b);
        }

        public int read(final byte[] b, final int off, final int len) throws IOException {
            checkCancel();
            return inputStream.read(b, off, len);
        }

        public long skip(final long n) throws IOException {
            checkCancel();
            return inputStream.skip(n);
        }

        public int available() throws IOException {
            checkCancel();
            return inputStream.available();
        }

        public void close() throws IOException {
            checkCancel();
            inputStream.close();
        }

        public void mark(final int readlimit) {
            inputStream.mark(readlimit);
        }

        public void reset() throws IOException {
            checkCancel();
            inputStream.reset();
        }

        public boolean markSupported() {
            return inputStream.markSupported();
        }
    }
}
