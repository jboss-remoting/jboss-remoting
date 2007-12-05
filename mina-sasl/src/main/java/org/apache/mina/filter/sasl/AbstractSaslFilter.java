package org.apache.mina.filter.sasl;

import java.io.IOException;
import org.apache.mina.common.AttributeKey;
import org.apache.mina.common.IoBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.WriteRequest;
import org.apache.mina.common.WriteRequestWrapper;

import javax.security.sasl.SaslException;

/**
 * Base class for SASL filters.  Contains common code between client and server SASL filters.
 */
public abstract class AbstractSaslFilter extends IoFilterAdapter {
    /**
     * A SASL attribute key that holds a boolean value, signifying whether encryption is enabled.
     */
    private static final AttributeKey SASL_WRAP = new AttributeKey(SaslClientFilter.class, "saslWrap");

    /**
     * The message sender for SASL messages.
     */
    private final SaslMessageSender messageSender;

    /**
     * Construct a SASL filter.
     *
     * @param messageSender the message sender
     */
    protected AbstractSaslFilter(final SaslMessageSender messageSender) {
        this.messageSender = messageSender;
    }

    /**
     * Determine if encryption is currently enabled.
     *
     * @param ioSession the IO session
     *
     * @return {@code true} if encryption is enabled
     */
    protected final boolean isWrap(IoSession ioSession) {
        return ((Boolean) ioSession.getAttribute(SASL_WRAP, Boolean.FALSE)).booleanValue();
    }

    public void messageReceived(NextFilter nextFilter, IoSession ioSession, Object msg) throws Exception {
        if (isWrap(ioSession)) {
            final IoBuffer buffer = (IoBuffer) msg;
            final byte[] bytes = buffer.array();
            final int offs = buffer.arrayOffset();
            final int limit = buffer.limit();
            final int pos = buffer.position();
            final byte[] unwrapped = unwrap(ioSession, bytes, offs + pos, limit - pos);
            nextFilter.messageReceived(ioSession, IoBuffer.wrap(unwrapped));
        } else {
            nextFilter.messageReceived(ioSession, msg);
        }
    }

    public void messageSent(NextFilter nextFilter, IoSession ioSession, WriteRequest writeRequest) throws Exception {
        if (writeRequest.getMessage() instanceof WrappedWriteRequest) {
            nextFilter.messageSent(ioSession, ((WrappedWriteRequest) writeRequest).getParentRequest());
        } else {
            nextFilter.messageSent(ioSession, writeRequest);
        }
    }

    public void filterWrite(NextFilter nextFilter, IoSession ioSession, WriteRequest writeRequest) throws Exception {
        if (isWrap(ioSession)) {
            final IoBuffer buffer = (IoBuffer) writeRequest.getMessage();
            final byte[] bytes = buffer.array();
            final int offs = buffer.arrayOffset();
            final int limit = buffer.limit();
            final int pos = buffer.position();
            final byte[] wrapped = wrap(ioSession, bytes, offs + pos, limit - pos);
            nextFilter.filterWrite(ioSession, new WrappedWriteRequest(writeRequest, IoBuffer.wrap(wrapped)));
        } else {
            nextFilter.filterWrite(ioSession, writeRequest);
        }
    }

    /**
     * Wrap (encrypt) a block of data.
     *
     * @param ioSession the IO session
     * @param data the data to encrypt
     * @param offs the offset into the array
     * @param len the number of bytes in the message
     *
     * @return a block of encrypted data
     *
     * @throws SaslException if an error occurs
     *
     * @see javax.security.sasl.SaslClient#wrap(byte[],int,int)
     * @see javax.security.sasl.SaslServer#wrap(byte[],int,int)
     */
    protected abstract byte[] wrap(IoSession ioSession, byte[] data, int offs, int len) throws SaslException;

    /**
     * Unwrap (decrypt) a block of data.
     *
     * @param ioSession the IO session
     * @param data the data to decrypt
     * @param offs the offset into the array
     * @param len the number of bytes in the message
     *
     * @return a block of decrypted data
     *
     * @throws SaslException if an error occurs
     *
     * @see javax.security.sasl.SaslClient#unwrap(byte[],int,int)
     * @see javax.security.sasl.SaslServer#unwrap(byte[],int,int)
     */
    protected abstract byte[] unwrap(IoSession ioSession, byte[] data, int offs, int len) throws SaslException;

    /**
     * Signify that future message transfer will be encrypted.  If encryption is already enabled,
     * then calling this method has no effect.  If encryption is not negotatiated by the SASL exchange,
     * then this method has no effect.
     *
     * @param ioSession the IO session to start encrypting
     */
    public final void startEncryption(IoSession ioSession) {
        if (!isWrap(ioSession)) {
            final String qop = (String) getQop(ioSession);
            if (qop != null && (qop.equalsIgnoreCase("auth-int") || qop.equalsIgnoreCase("auth-conf"))) {
                ioSession.setAttribute(SASL_WRAP, Boolean.TRUE);
            }
        }
    }

    /**
     * Signify that future message transfer will not be encrypted.  If encryption is already disabled,
     * then calling this method has no effect.
     *
     * @param ioSession the IO session to stop encrypting
     */
    public final void endEncryption(IoSession ioSession) {
        ioSession.setAttribute(SASL_WRAP, Boolean.FALSE);
    }

    /**
     * Send a SASL negotiation message.
     *
     * @param ioSession the IO session
     * @param rawMsgData the raw message data
     *
     * @throws IOException if an error occurs
     */
    protected final void sendSaslMessage(IoSession ioSession, byte[] rawMsgData) throws IOException {
        messageSender.sendSaslMessage(ioSession, rawMsgData);
    }

    /**
     * Get the current value of the {@code Sasl.QOP} attribute.  Used to determine whether
     * encryption should be enabled.
     *
     * @param ioSession
     * @return
     */
    protected abstract String getQop(IoSession ioSession);

    private static class WrappedWriteRequest extends WriteRequestWrapper {
        private final IoBuffer encryptedMessage;

        private WrappedWriteRequest(WriteRequest writeRequest, IoBuffer encryptedMessage) {
            super(writeRequest);
            this.encryptedMessage = encryptedMessage;
        }

        public IoBuffer getMessage() {
            return encryptedMessage;
        }
    }
}
