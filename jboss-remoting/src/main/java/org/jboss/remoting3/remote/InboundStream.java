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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import org.jboss.marshalling.NioByteInput;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

final class InboundStream {
    private final int id;
    private final RemoteConnection remoteConnection;
    private final Receiver receiver;

    private State state;
    private static final Logger log = Loggers.main;

    InboundStream(final int id, final RemoteConnection remoteConnection, final Receiver receiver) {
        this.id = id;
        this.remoteConnection = remoteConnection;
        this.receiver = receiver;
    }

    InboundStream(final int id, final RemoteConnection remoteConnection, final ByteInputResult byteInputResult) {
        this.id = id;
        this.remoteConnection = remoteConnection;
        final NioByteInput byteInput = new NioByteInput(
                new NioByteInputHandler()
        );
        receiver = new NioByteInputReceiver(byteInput);
        byteInputResult.accept(byteInput, this);
    }

    InboundStream(final int id, final RemoteConnection remoteConnection, final OutputStream outputStream) {
        this.id = id;
        this.remoteConnection = remoteConnection;
        receiver = new OutputStreamReceiver(outputStream);
    }

    RemoteConnection getRemoteConnection() {
        return remoteConnection;
    }

    Receiver getReceiver() {
        return receiver;
    }

    enum State {
        WAITING_FIRST,
        WAITING_FIRST_EXCEPTION,
        RUNNING,
        CLOSE_WAIT,
        CLOSED
    }

    interface Receiver {
        void push(ByteBuffer buffer);

        void pushEof();

        void pushException();
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

    void sendAsyncClose() {
        synchronized (this) {
            OUT: for (;;) switch (state) {
                case WAITING_FIRST_EXCEPTION: {
                    return;
                }
                case WAITING_FIRST: {
                    try {
                        wait();
                        break;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        if (state == State.WAITING_FIRST) {
                            state = State.WAITING_FIRST_EXCEPTION;
                            notifyAll();
                            return;
                        }
                        continue;
                    }
                }
                case RUNNING: {
                    state = State.CLOSE_WAIT;
                    break OUT;
                }
                case CLOSE_WAIT: {
                    state = State.CLOSED;
                    break OUT;
                }
                case CLOSED: {
                    return;
                }
            }
            doSend(RemoteProtocol.STREAM_ASYNC_CLOSE);
        }
    }

    void sendAsyncException() {
        synchronized (this) {
            switch (state) {
                case RUNNING: {
                    state = State.CLOSE_WAIT;
                    break;
                }
                case CLOSE_WAIT: {
                    state = State.CLOSED;
                    break;
                }
                case CLOSED: {
                    return;
                }
            }
            doSend(RemoteProtocol.STREAM_ASYNC_EXCEPTION);
        }
    }

    void sendAsyncStart() {
        doSend(RemoteProtocol.STREAM_ASYNC_START);
    }

    void sendAck() {
        synchronized (this) {
            switch (state) {
                case RUNNING: {
                    state = State.CLOSE_WAIT;
                    break;
                }
                case CLOSE_WAIT: {
                    state = State.CLOSED;
                    break;
                }
                case CLOSED: {
                    return;
                }
            }
            doSend(RemoteProtocol.STREAM_ASYNC_EXCEPTION);
        }
    }

    private final class NioByteInputHandler implements  NioByteInput.InputHandler {

        public void acknowledge() {
            sendAck();
        }

        public void close() throws IOException {
            sendAsyncClose();
        }
    }

    private final class NioByteInputReceiver implements Receiver, NioByteInput.BufferReturn {
        private final NioByteInput nioByteInput;

        NioByteInputReceiver(final NioByteInput nioByteInput) {
            this.nioByteInput = nioByteInput;
        }

        public void push(final ByteBuffer buffer) {
            nioByteInput.push(buffer, this);
        }

        public void pushEof() {
            nioByteInput.pushEof();
        }

        public void pushException() {
            nioByteInput.pushException(new IOException("Remote stream exception occurred on forwarded stream"));
        }

        public void returnBuffer(final ByteBuffer buffer) {
            remoteConnection.free(buffer);
        }
    }

    private class OutputStreamReceiver implements Receiver {

        private final OutputStream outputStream;

        OutputStreamReceiver(final OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        public void push(final ByteBuffer buffer) {
            try {
                if (buffer.hasArray()) {
                    final byte[] array = buffer.array();
                    final int offs = buffer.arrayOffset() + buffer.position();
                    final int len = buffer.remaining();
                    outputStream.write(array, offs, len);
                } else {
                    final byte[] array = new byte[buffer.remaining()];
                    buffer.get(array);
                    outputStream.write(array);
                }
            } catch (IOException e) {
                log.trace("Output stream write failed: %s", e);
                sendAsyncException();
            }
        }

        public void pushEof() {
            IoUtils.safeClose(outputStream);
        }

        public void pushException() {
            IoUtils.safeClose(outputStream);
        }
    }

    interface ByteInputResult {
        void accept(NioByteInput nioByteInput, final InboundStream inboundStream);
    }
}
