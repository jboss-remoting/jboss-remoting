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

package org.jboss.remoting3.remote;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.util.concurrent.Semaphore;

final class OutboundStream {

    private final int id;
    private final RemoteConnection remoteConnection;
    private final Semaphore semaphore = new Semaphore(3);

    private State state = State.WAITING;

    enum State {

        WAITING,
        WAITING_EXCEPTION,
        RUNNING,
        ASYNC_CLOSE,
        ASYNC_EXCEPTION,
        CLOSE_WAIT, // close/exception sent, waiting for async close/exception
        CLOSED,
    }

    OutboundStream(final int id, final RemoteConnection remoteConnection) {
        this.id = id;
        this.remoteConnection = remoteConnection;
    }

    /**
     * Get the next buffer.
     *
     * @return the next buffer
     */
    ByteBuffer getBuffer() {
        final ByteBuffer buffer = remoteConnection.allocate();
        buffer.position(4);
        buffer.put(RemoteProtocol.STREAM_DATA);
        buffer.putInt(id);
        return buffer;
    }

    /**
     * Send a buffer acquired above.
     *
     * @return {@code false} if writing should cease
     *
     * @throws java.io.IOException in the event of an async close or exception
     */
    void send(ByteBuffer buffer) throws IOException {
        try {
            synchronized (this) {
                OUT: for (;;) switch (state) {
                    case WAITING: {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new InterruptedIOException();
                        }
                        continue;
                    }
                    case ASYNC_CLOSE: {
                        state = State.CLOSED;
                        sendEof();
                        throw new AsynchronousCloseException();
                    }
                    case ASYNC_EXCEPTION: {
                        state = State.CLOSED;
                        throw new AsynchronousCloseException(); // todo pick a better exception
                    }
                    case CLOSE_WAIT:
                    case CLOSED: {
                        throw new AsynchronousCloseException(); // todo pick a better exception
                    }
                    case RUNNING: {
                        break OUT;
                    }
                    default: {
                        throw new IllegalStateException();
                    }
                }
            }
            remoteConnection.sendBlocking(buffer, true);
        } finally {
            remoteConnection.free(buffer);
        }
    }

    void sendEof() {
        synchronized (this) {
            switch (state) {
                case WAITING: {
                    state = State.CLOSE_WAIT;
                    return;
                }
                case ASYNC_EXCEPTION:
                case ASYNC_CLOSE: {
                    state = State.CLOSED;
                    break;
                }

            }
            doSend(RemoteProtocol.STREAM_CLOSE);
        }
    }

    private void doSend(byte code) {
        final ByteBuffer buffer = remoteConnection.allocate();
        buffer.position(4);
        buffer.put(code);
        buffer.putInt(id);
        buffer.flip();
        try {
            remoteConnection.sendBlocking(buffer, true);
        } catch (IOException e) {
            // irrelevant
        }
    }

    void sendException() {
        synchronized (this) {
            if (state == State.WAITING) {
                state = State.WAITING_EXCEPTION;
                return;
            } else {
                state = State.CLOSE_WAIT;
            }
            doSend(RemoteProtocol.STREAM_EXCEPTION);
        }
    }

    void asyncStart() {
        synchronized (this) {
            switch (state) {
                case WAITING: {
                    state = State.RUNNING;
                    notifyAll();
                    return;
                }
                case WAITING_EXCEPTION: {
                    state = State.CLOSE_WAIT;
                    notifyAll();
                    sendException();
                }
                case RUNNING:
                case ASYNC_CLOSE:
                case ASYNC_EXCEPTION:
                case CLOSE_WAIT:
                case CLOSED:
            }
            if (state == State.WAITING_EXCEPTION) {
                state = State.CLOSED;
                doSend(RemoteProtocol.STREAM_EXCEPTION);
                return;
            }
        }
    }

    void ack() {
        semaphore.release();
    }

    void asyncClose() {
        synchronized (this) {
            switch (state) {
                case WAITING:
                case RUNNING:

                {
                    doSend(RemoteProtocol.STREAM_CLOSE);
                    state = State.CLOSED;
                    notifyAll();
                    return;
                }
            }
        }
    }

    void asyncException() {

    }

}
