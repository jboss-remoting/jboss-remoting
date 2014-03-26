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

package org.jboss.remoting3.remote;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.jboss.remoting3.MessageCancelledException;
import org.jboss.remoting3.MessageInputStream;
import org.xnio.Pooled;
import org.xnio.streams.BufferPipeInputStream;

import static java.lang.Thread.holdsLock;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class InboundMessage {
    final short messageId;
    final RemoteConnectionChannel channel;
    int inboundWindow;
    boolean streamClosed;
    boolean closeSent;
    boolean eofReceived;
    boolean cancelled;
    long remaining;

    static final IntIndexer<InboundMessage> INDEXER = new IntIndexer<InboundMessage>() {
        public int getKey(final InboundMessage argument) {
            return argument.messageId & 0xffff;
        }

        public boolean equals(final InboundMessage argument, final int index) {
            return (argument.messageId & 0xffff) == index;
        }
    };

    InboundMessage(final short messageId, final RemoteConnectionChannel channel, int inboundWindow, final long maxInboundMessageSize) {
        this.messageId = messageId;
        this.channel = channel;
        this.inboundWindow = inboundWindow;
        remaining = maxInboundMessageSize;
    }

    final BufferPipeInputStream inputStream = new BufferPipeInputStream(new BufferPipeInputStream.InputHandler() {
        public void acknowledge(final Pooled<ByteBuffer> acked) throws IOException {
            doAcknowledge(acked);
        }

        public void close() throws IOException {
            doClose();
        }
    });

    private void doClose() {
        assert holdsLock(inputStream);
        if (streamClosed) {
            // idempotent
            return;
        }
        streamClosed = true;
        // on close, send close message
        doSendCloseMessage();
        // but keep the mapping around until we receive our EOF
        // else just keep discarding data until the EOF comes in.
    }

    private void doSendCloseMessage() {
        assert holdsLock(inputStream);
        if (closeSent || ! channel.getConnectionHandler().isMessageClose()) {
            // we don't send a MESSAGE_CLOSE because broken versions will simply stop sending packets, and we won't know when the message is really gone.
            // the risk is that the remote side could have started a new message in the meantime, and our MESSAGE_CLOSE would kill the wrong message.
            // so this behavior is better than the alternative.
            return;
        }
        Pooled<ByteBuffer> pooled = allocate(Protocol.MESSAGE_CLOSE);
        boolean ok = false;
        try {
            ByteBuffer buffer = pooled.getResource();
            buffer.flip();
            channel.getRemoteConnection().send(pooled);
            ok = true;
            closeSent = true;
        } finally {
            if (! ok) pooled.free();
        }
    }

    private void doAcknowledge(final Pooled<ByteBuffer> acked) {
        assert holdsLock(inputStream);
        if (eofReceived) {
            // no ack needed; also a best-effort to work around broken peers
            return;
        }
        final boolean badMsgSize = channel.getConnectionHandler().isFaultyMessageSize();
        int consumed = acked.getResource().position();
        if (! badMsgSize) consumed -= 8; // position minus header length (not including framing size)
        inboundWindow += consumed;
        Pooled<ByteBuffer> pooled = allocate(Protocol.MESSAGE_WINDOW_OPEN);
        boolean ok = false;
        try {
            ByteBuffer buffer = pooled.getResource();
            buffer.putInt(consumed); // Open window by buffer size
            buffer.flip();
            channel.getRemoteConnection().send(pooled);
            ok = true;
        } finally {
            if (! ok) pooled.free();
        }
    }

    final MessageInputStream messageInputStream = new MessageInputStream() {
        public int read() throws IOException {
            synchronized (inputStream) {
                if (cancelled) {
                    throw new MessageCancelledException();
                }
                return inputStream.read();
            }
        }

        public int read(final byte[] bytes, final int offs, final int length) throws IOException {
            synchronized (inputStream) {
                if (cancelled) {
                    throw new MessageCancelledException();
                }
                return inputStream.read(bytes, offs, length);
            }
        }

        public long skip(final long l) throws IOException {
            synchronized (inputStream) {
                if (cancelled) {
                    throw new MessageCancelledException();
                }
                return inputStream.skip(l);
            }
        }

        public int available() throws IOException {
            synchronized (inputStream) {
                if (cancelled) {
                    throw new MessageCancelledException();
                }
                return inputStream.available();
            }
        }

        public void close() throws IOException {
            synchronized (inputStream) {
                if (cancelled) {
                    throw new MessageCancelledException();
                }
                inputStream.close();
            }
        }
    };

    Pooled<ByteBuffer> allocate(byte protoId) {
        Pooled<ByteBuffer> pooled = channel.allocate(protoId);
        ByteBuffer buffer = pooled.getResource();
        buffer.putShort(messageId);
        return pooled;
    }

    void handleIncoming(Pooled<ByteBuffer> pooledBuffer) {
        boolean eof;
        synchronized (inputStream) {
            ByteBuffer buffer = pooledBuffer.getResource();
            final int bufRemaining = buffer.remaining();
            if ((inboundWindow -= bufRemaining) < 0) {
                channel.getRemoteConnection().handleException(new IOException("Input overrun"));
            }
            buffer.position(buffer.position() - 1);
            byte flags = buffer.get();

            eof = (flags & Protocol.MSG_FLAG_EOF) != 0;
            boolean cancelled = (flags & Protocol.MSG_FLAG_CANCELLED) != 0;
            if (bufRemaining > remaining) {
                cancelled = true;
                doClose();
            }
            if (cancelled) {
                this.cancelled = true;
                // make sure it goes through
                inputStream.pushException(new MessageCancelledException());
            }
            if (streamClosed) {
                // ignore, but keep the bits flowing
                if (! eof && ! closeSent) {
                    // we don't need to acknowledge if it's EOF or if we sent a close msg since no more data is coming anyway
                    buffer.position(buffer.limit()); // "consume" everything
                    doAcknowledge(pooledBuffer);
                }
                pooledBuffer.free();
            } else if (! cancelled) {
                remaining -= bufRemaining;
                inputStream.push(pooledBuffer);
            }
            if (eof) {
                eofReceived = true;
                if (!streamClosed) {
                    inputStream.pushEof();
                }
                channel.freeInboundMessage(messageId);
                // if the peer is old, they might reuse the ID now regardless of us; if new, we have to send the close message to acknowledge the remainder
                doSendCloseMessage();
            }
        }
    }

    void handleDuplicate() {
        // this method is called when the remote side forgot about us.  Our mapping will have been already replaced.
        // We must not send anything to the peer from here on because things may be in a broken state.
        // Though this is a best-effort strategy as everything is screwed up in this case anyway.
        RemoteLogger.conn.duplicateMessageId(messageId, channel.getRemoteConnection().getChannel().getPeerAddress());
        synchronized (inputStream) {
            if (! streamClosed) {
                eofReceived = true; // it wasn't really, but we should act like it was
                closeSent = true; // we didn't really, but we should act like we did
                cancelled = true; // just not the usual way...
                inputStream.pushException(RemoteLogger.conn.duplicateMessageIdException());
            }
        }
    }

    void dumpState(final StringBuilder b) {
        b.append("            ").append(String.format("Inbound message ID %04x, window %d\n", messageId & 0xFFFF, inboundWindow));
        b.append("            ").append("* flags: ");
        if (cancelled) b.append("cancelled ");
        if (closeSent) b.append("close-sent ");
        if (streamClosed) b.append("stream-closed ");
        if (eofReceived) b.append("eof-received ");
        b.append('\n');
    }
}
