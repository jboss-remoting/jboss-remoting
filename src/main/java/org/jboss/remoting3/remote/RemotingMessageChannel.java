package org.jboss.remoting3.remote;

import org.jboss.logging.Logger;
import org.jboss.remoting3.RemotingOptions;
import org.xnio.Buffers;
import org.xnio.IoUtils;
import org.xnio.Pooled;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.TranslatingSuspendableChannel;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import org.xnio.BufferAllocator;

/**
 * This class is alternative to {@link org.xnio.channels.FramedMessageChannel} to fix
 * <a href="https://issues.jboss.org/browse/REM3-259">REM3-259</a> issue.
 *
 * While slightly modified, it is basically a copy of <code>FramedMessageChannel</code>
 *
 * @author rnetuka@redhat.com
 */
public class RemotingMessageChannel extends TranslatingSuspendableChannel<ConnectedMessageChannel, ConnectedStreamChannel> implements ConnectedMessageChannel {

    static class AdjustedBuffer {
        private final Pooled<ByteBuffer> original;
        private Pooled<ByteBuffer> adjusted;
        AdjustedBuffer(Pooled<ByteBuffer> original) {
            this.original = original;
        }
        Pooled<ByteBuffer> getAdjustedBuffer() {
            return adjusted == null ? original : adjusted;
        }
    }

    private static final Logger log = Logger.getLogger("org.jboss.remoting");

    private Pooled<ByteBuffer> receiveBuffer;
    private ByteBuffer lengthBuffer = ByteBuffer.allocate(4);
    private Pooled<ByteBuffer> transmitBuffer;

    private final Object readLock = new Object();
    private final Object writeLock = new Object();

    /**
     * Message length peeked (checked) in advance prior to calling {@link #receive(ByteBuffer)} in order to ensure
     * buffer capacities. If <code>null</code>, the
     */
    private Integer messageLength;


    /**
     * Construct a new instance.
     *
     * @param channel the channel to wrap
     * @param receiveBuffer the receive buffer (should be direct)
     * @param transmitBuffer the send buffer (should be direct)
     */
    public RemotingMessageChannel(ConnectedStreamChannel channel, ByteBuffer receiveBuffer, ByteBuffer transmitBuffer) {
        super(channel);
        this.receiveBuffer = Buffers.pooledWrapper(receiveBuffer);
        this.transmitBuffer = Buffers.pooledWrapper(transmitBuffer);
        log.tracef("Created new framed message channel around %s, receive buffer %s, transmit buffer %s", channel, receiveBuffer, transmitBuffer);
    }


    /**
     * Checks if the message length was peeked in advance. If so, it can be read from {@link #messageLength} field. This
     * is only useful  during execution {@link #receive(ByteBuffer)} method. Outside of this method, it always returs
     * <code>false</code>.
     *
     * @return  <code>true</code> if message length was peeked before actual reading
     */
    private boolean messageLengthPeeked() {
        return messageLength != null;
    }

    /**
     * Reads the message length without reading the message itself.
     *
     * @return  message length in bytes
     *
     * @throws  IOException
     *          if the message length couldn't be read
     */
    private int readMessageLength() throws IOException {
        synchronized (readLock) {
            if (messageLengthPeeked()) {
                log.tracef("Already read a length");
                return 0;
            }
            int res = channel.read(lengthBuffer);
            if (lengthBuffer.position() < 4) {
                if (res == -1) {
                    lengthBuffer.clear();
                }
                log.tracef("Did not read a length");
                clearReadReady();
                return res;
            }
            lengthBuffer.flip();
            int length = lengthBuffer.getInt();
            if (length < 0) {
                throw new IOException("Unable to read message length. Invalid value of " + length);
            }
            messageLength = length;
            lengthBuffer.clear();
            return length;
        }
    }

    /**
     * Adjusts inner buffers to required message length. For security reason, the buffer size cannot exceed value
     * specified in {@link RemotingOptions#MAX_RECEIVE_BUFFER_SIZE}
     *
     * @param   length
     *          message length the buffers
     *
     * @throws  IllegalArgumentException
     *          if requested length exceeds maximal allowed buffer size
     */
    void adjustToMessageLength(int length) {
        if (length > RemotingOptions.MAX_RECEIVE_BUFFER_SIZE) {
            throw new IllegalArgumentException("Unable to adjust to message size. For security reason, the maximal buffer size is set to " + RemotingOptions.MAX_RECEIVE_BUFFER_SIZE);
        }
        if (length > receiveBuffer.getResource().capacity()) {
            receiveBuffer = Buffers.pooledWrapper(ByteBuffer.allocate(length + 4));
        }
        if (length > transmitBuffer.getResource().capacity()) {
            transmitBuffer = Buffers.pooledWrapper(ByteBuffer.allocate(length + 4));
        }
    }

    int receive(final AdjustedBuffer buffer) throws IOException {
        synchronized (readLock) {
            if (isReadShutDown()) {
                return -1;
            }
            int messageLength = readMessageLength();
            if (messageLength <= 0) {
                return messageLength;
            }
            if (messageLength > buffer.original.getResource().capacity() && messageLength < RemotingOptions.MAX_RECEIVE_BUFFER_SIZE) {
                buffer.adjusted = Buffers.allocatedBufferPool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, messageLength).allocate();
                adjustToMessageLength(messageLength);        
            }
            final ByteBuffer receiveBuffer = buffer.getAdjustedBuffer().getResource();
            return receive(receiveBuffer);
        }
    }
    
    /** {@inheritDoc} */
    public int receive(final ByteBuffer buffer) throws IOException {
        synchronized (readLock) {
            if (isReadShutDown()) {
                return -1;
            }
            final ByteBuffer receiveBuffer = this.receiveBuffer.getResource();
            int res = 0;
            final ConnectedStreamChannel channel = (ConnectedStreamChannel) this.channel;
            do {
                res = channel.read(receiveBuffer);
            } while (res > 0);

            if (! messageLengthPeeked()) { // message length hasn't been read in advance. The first 4 bytes form the length information.
                if (receiveBuffer.position() < 4) {
                    if (res == -1) {
                        receiveBuffer.clear();
                    }
                    log.tracef("Did not read a length");
                    clearReadReady();
                    // must be <= 0
                    return res;
                }
            }

            receiveBuffer.flip();

            try {
                int length;

                if (messageLengthPeeked()) {
                    length = messageLength;
                } else {
                    length = receiveBuffer.getInt();
                    if (length < 0 || length > receiveBuffer.capacity() - 4) {
                        Buffers.unget(receiveBuffer, 4);
                        throw new IOException("Received an invalid message length of " + length);
                    }
                }
                if (receiveBuffer.remaining() < length) {
                    if (res == -1) {
                        receiveBuffer.clear();
                    } else {
                        Buffers.unget(receiveBuffer, 4);
                        receiveBuffer.compact();
                    }
                    log.tracef("Did not read enough bytes for a full message");
                    clearReadReady();
                    // must be <= 0
                    return res;
                }
                if (buffer.hasRemaining()) {
                    log.tracef("Copying message from %s into %s", receiveBuffer, buffer);
                    Buffers.copy(buffer, Buffers.slice(receiveBuffer, length));
                } else {
                    log.tracef("Not copying message from %s into full buffer %s", receiveBuffer, buffer);
                    Buffers.skip(receiveBuffer, length);
                }
                // move on to next message
                receiveBuffer.compact();
                return length;
            } finally {
                messageLength = null;

                if (res != -1) {
                    if (receiveBuffer.position() >= 4 && receiveBuffer.position() >= 4 + receiveBuffer.getInt(0)) {
                        // there's another packet ready to go
                        setReadReady();
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    public long receive(final ByteBuffer[] buffers) throws IOException {
        return receive(buffers, 0, buffers.length);
    }

    /** {@inheritDoc} */
    public long receive(final ByteBuffer[] buffers, final int offs, final int len) throws IOException {
        synchronized (readLock) {
            if (isReadShutDown()) {
                return -1;
            }
            final ByteBuffer receiveBuffer = this.receiveBuffer.getResource();
            int res = 0;
            final ConnectedStreamChannel channel = (ConnectedStreamChannel) this.channel;
            do {
                res = channel.read(receiveBuffer);
            } while (res > 0);
            if (receiveBuffer.position() < 4) {
                if (res == -1) {
                    receiveBuffer.clear();
                }
                log.tracef("Did not read a length");
                clearReadReady();
                return res;
            }
            receiveBuffer.flip();
            try {
                final int length = receiveBuffer.getInt();
                if (length < 0 || length > receiveBuffer.capacity() - 4) {
                    Buffers.unget(receiveBuffer, 4);
                    throw new IOException("Received an invalid message length of " + length);
                }
                if (receiveBuffer.remaining() < length) {
                    if (res == -1) {
                        receiveBuffer.clear();
                    } else {
                        Buffers.unget(receiveBuffer, 4);
                        receiveBuffer.compact();
                    }
                    log.tracef("Did not read enough bytes for a full message");
                    clearReadReady();
                    // must be <= 0
                    return res;
                }
                if (Buffers.hasRemaining(buffers)) {
                    log.tracef("Copying message from %s into multiple buffers", receiveBuffer);
                    Buffers.copy(buffers, offs, len, Buffers.slice(receiveBuffer, length));
                } else {
                    log.tracef("Not copying message from %s into multiple full buffers", receiveBuffer);
                    Buffers.skip(receiveBuffer, length);
                }
                // move on to next message
                receiveBuffer.compact();
                return length;
            } finally {
                if (res != -1) {
                    if (receiveBuffer.position() >= 4 && receiveBuffer.position() >= 4 + receiveBuffer.getInt(0)) {
                        // there's another packet ready to go
                        setReadReady();
                    }
                }
            }
        }
    }

    protected void shutdownReadsAction(final boolean writeComplete) throws IOException {
        synchronized (readLock) {
            log.tracef("Shutting down reads on %s", this);
            try {
                receiveBuffer.getResource().clear();
                lengthBuffer.clear();
            } catch (Throwable t) {
            }
            try {
                receiveBuffer.free();
            } catch (Throwable t) {
            }
        }
        channel.shutdownReads();
    }

    /** {@inheritDoc} */
    public boolean send(final ByteBuffer buffer) throws IOException {
        synchronized (writeLock) {
            if (isWriteShutDown()) {
                throw new EOFException("Writes have been shut down");
            }
            if (!buffer.hasRemaining()) {
                return true;
            }
            final ByteBuffer transmitBuffer = this.transmitBuffer.getResource();
            final int remaining = buffer.remaining();
            if (remaining > transmitBuffer.capacity() - 4) {
                throw new IOException("Transmitted message is too large");
            }
            log.tracef("Accepting %s into %s", buffer, transmitBuffer);
            if (transmitBuffer.remaining() < 4 + remaining && ! doFlushBuffer()) {
                log.tracef("Insufficient room to accept %s into %s", buffer, transmitBuffer);
                return false;
            }
            transmitBuffer.putInt(remaining);
            transmitBuffer.put(buffer);
            log.tracef("Accepted a message into %s", transmitBuffer);
            doFlush();
            return true;
        }
    }

    /** {@inheritDoc} */
    public boolean send(final ByteBuffer[] buffers) throws IOException {
        return send(buffers, 0, buffers.length);
    }

    /** {@inheritDoc} */
    public boolean send(final ByteBuffer[] buffers, final int offs, final int len) throws IOException {
        synchronized (writeLock) {
            if (isWriteShutDown()) {
                throw new EOFException("Writes have been shut down");
            }
            if (!Buffers.hasRemaining(buffers, offs, len)) {
                return true;
            }
            final ByteBuffer transmitBuffer = this.transmitBuffer.getResource();
            final long remaining = Buffers.remaining(buffers, offs, len);
            if (remaining > transmitBuffer.capacity() - 4L) {
                throw new IOException("Transmitted message is too large");
            }
            log.tracef("Accepting multiple buffers into %s", transmitBuffer);
            if (transmitBuffer.remaining() < 4 + remaining && ! doFlushBuffer()) {
                log.tracef("Insufficient room to accept multiple buffers into %s", transmitBuffer);
                return false;
            }
            transmitBuffer.putInt((int) remaining);
            Buffers.copy(transmitBuffer, buffers, offs, len);
            log.tracef("Accepted a message into %s", transmitBuffer);
            doFlush();
            return true;
        }
    }

    protected boolean flushAction(final boolean shutDown) throws IOException {
        synchronized (writeLock) {
            return (doFlushBuffer()) && channel.flush();
        }
    }

    protected void shutdownWritesComplete(final boolean readShutDown) throws IOException {
        synchronized (writeLock) {
            log.tracef("Finished shutting down writes on %s", this);
            try {
                transmitBuffer.free();
            } catch (Throwable t) {}
        }
        channel.shutdownWrites();
    }

    private boolean doFlushBuffer() throws IOException {
        assert Thread.holdsLock(writeLock);
        final ByteBuffer buffer = transmitBuffer.getResource();
        buffer.flip();
        try {
            while (buffer.hasRemaining()) {
                final int res = channel.write(buffer);
                if (res == 0) {
                    log.tracef("Did not fully flush %s", this);
                    return false;
                }
            }
            log.tracef("Fully flushed %s", this);
            return true;
        } finally {
            buffer.compact();
        }
    }

    private boolean doFlush() throws IOException {
        return doFlushBuffer() && channel.flush();
    }

    protected void closeAction(final boolean readShutDown, final boolean writeShutDown) throws IOException {
        boolean error = false;
        if (! writeShutDown) {
            synchronized (writeLock) {
                try {
                    if (! doFlush()) error = true;
                } catch (Throwable t) {
                    error = true;
                }
                try {
                    transmitBuffer.free();
                } catch (Throwable t) {
                }
            }
        }
        if (! readShutDown) {
            synchronized (readLock) {
                try {
                    receiveBuffer.free();
                } catch (Throwable t) {
                }
            }
        }
        try {
            if (error) throw new IOException("Unflushed data truncated");
            channel.close();
        } finally {
            IoUtils.safeClose(channel);
        }
    }

    /** {@inheritDoc} */
    public SocketAddress getPeerAddress() {
        return channel.getPeerAddress();
    }

    /** {@inheritDoc} */
    public <A extends SocketAddress> A getPeerAddress(final Class<A> type) {
        return channel.getPeerAddress(type);
    }

    /** {@inheritDoc} */
    public SocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    /** {@inheritDoc} */
    public <A extends SocketAddress> A getLocalAddress(final Class<A> type) {
        return channel.getLocalAddress(type);
    }

    /**
     * Get the underlying channel.
     *
     * @return the underlying channel
     */
    public ConnectedStreamChannel getChannel() {
        return channel;
    }
}
