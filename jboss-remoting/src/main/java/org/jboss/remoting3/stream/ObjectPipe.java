/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.stream;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.EOFException;
import java.util.ArrayDeque;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.Queue;
import java.util.NoSuchElementException;

/**
 * A pipe for objects.  Typically, data is written to the sink side of the pipe from one thread while being read from
 * the source side in another thread.  Object pipes are useful in the case that you send an {@link
 * org.jboss.remoting3.stream.ObjectSink ObjectSink} to a remote system in a request, but you want to read the objects
 * from an {@link org.jboss.remoting3.stream.ObjectSource ObjectSource}.
 */
public final class ObjectPipe<T> {

    private final Lock queueLock = new ReentrantLock();
    // signal on write, await on read
    private final Condition writeCondition = queueLock.newCondition();
    // signal on read, await on write
    private final Condition readCondition = queueLock.newCondition();

    private final Queue<T> queue;

    private final Source source = new Source();
    private final Sink sink = new Sink();
    private final int max;
    private boolean open = true;

    /**
     * Create an object pipe with the given maximum buffer size.
     *
     * @param max the maximum number of buffered objects
     */
    public ObjectPipe(int max) {
        this.max = max;
        queue = new ArrayDeque<T>(max);
    }

    /**
     * Get the source end of the pipe, from which objects may be read.
     *
     * @return the source end
     */
    public ObjectSource<T> getSource() {
        return source;
    }

    /**
     * Get the sink end of the pipe, to which objects may be written.
     *
     * @return the sink end
     */
    public ObjectSink<T> getSink() {
        return sink;
    }

    private class Source implements ObjectSource<T> {

        public boolean hasNext() throws IOException {
            final Lock queueLock = ObjectPipe.this.queueLock;
            final Condition writeCondition = ObjectPipe.this.writeCondition;
            final Queue<T> queue = ObjectPipe.this.queue;
            try {
                queueLock.lockInterruptibly();
                try {
                    while (open && queue.isEmpty()) {
                        writeCondition.await();
                    }
                    return open || !queue.isEmpty();
                } finally {
                    queueLock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("hasNext() was interrupted");
            }
        }

        public T next() throws IOException {
            final Lock queueLock = ObjectPipe.this.queueLock;
            final Queue<T> queue = ObjectPipe.this.queue;
            try {
                queueLock.lockInterruptibly();
                try {
                    final T t = queue.poll();
                    if (t == null) {
                        if (open) {
                            throw new NoSuchElementException();
                        } else {
                            throw new EOFException("EOF on next()");
                        }
                    }
                    readCondition.signal();
                    return t;
                } finally {
                    queueLock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("hasNext() was interrupted");
            }
        }

        /**
         * Closing the reader breaks everything.  All unread items are discarded, all waiters are woken up.
         */
        public void close() {
            final Lock queueLock = ObjectPipe.this.queueLock;
            queueLock.lock();
            try {
                if (open) {
                    open = false;
                    queue.clear();
                    writeCondition.signalAll();
                    readCondition.signalAll();
                }
            } finally {
                queueLock.unlock();
            }
        }

        protected void finalize() throws Throwable {
            close();
            super.finalize();
        }
    }

    private class Sink implements ObjectSink<T> {

        public void accept(final T instance) throws IOException {
            final int max = ObjectPipe.this.max;
            final Queue<T> queue = ObjectPipe.this.queue;
            final Lock queueLock = ObjectPipe.this.queueLock;
            try {
                queueLock.lockInterruptibly();
                try {
                    while (open && queue.size() == max) {
                        readCondition.await();
                    }
                    if (!open) {
                        throw new EOFException("pipe closed");
                    }
                } finally {
                    queueLock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("accept(T) was interrupted");
            }
        }

        /**
         * Do nothing since nothing can be done - clearing our internal state depends on the reader.
         */
        public void flush() {
        }

        /**
         * Closing the writer just clears the open flag and notifies waiters.
         */
        public void close() {
            final Lock queueLock = ObjectPipe.this.queueLock;
            queueLock.lock();
            try {
                if (!open) return;
                open = false;
                // readers might be waiting
                if (queue.isEmpty()) {
                    readCondition.signalAll();
                } else {
                    readCondition.signal();
                }
                // other writers might also be waiting - they should be killed
                writeCondition.signalAll();
            } finally {
                queueLock.unlock();
            }
        }

        protected void finalize() throws Throwable {
            close();
            super.finalize();
        }
    }
}
