package org.apache.mina.filter.sasl;

import java.io.IOException;
import org.apache.mina.common.AttributeKey;
import org.apache.mina.common.IoSession;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

/**
 * An {@code IoFilter} implementation for handling the server side of SASL.  The filter directly handles
 * the wrapping and unwrapping of messages that are encrypted or otherwise processed by a SASL mechanism.
 * Also, the filter indirectly handles the negotiation of protocol messages.
 * </p>
 * Since SASL often encodes the negotiation messages in the higher-level protocol, when a SASL challenge
 * response message comes in, it is up to the protocol handler to decode these messages.  Once a response message
 * is decoded, it can be sent directly into this filter via the
 * {@link #handleSaslResponse(org.apache.mina.common.IoSession, byte[])} method.  This method will evaluate
 * the response and, if necessary, send another challenge using the provided {@link org.apache.mina.filter.sasl.SaslMessageSender}
 * instance.
 * </p>
 * The completion of the negotiation may be tested with the {@link #isComplete(org.apache.mina.common.IoSession)} method.
 *
 * @see javax.security.sasl.SaslServer
 */
public final class SaslServerFilter extends AbstractSaslFilter {
    private static final AttributeKey SASL_SERVER_KEY = new AttributeKey(SaslServerFilter.class, "saslServer");

    /**
     * Construct a new SASL server filter.
     *
     * @param messageSender the message sender, used to send challenge messages
     */
    public SaslServerFilter(final SaslMessageSender messageSender) {
        super(messageSender);
    }

    /**
     * Get the {@code SaslServer} instance for the given session.
     *
     * @param ioSession the session
     * @return the SASL server instance
     */
    public SaslServer getSaslServer(IoSession ioSession) {
        return (SaslServer) ioSession.getAttribute(SASL_SERVER_KEY);
    }

    /**
     * Set the {@code SaslServer} instance for the given session.
     *
     * @param ioSession the session
     * @param saslServer the SASL server instance
     */
    public void setSaslServer(IoSession ioSession, SaslServer saslServer) {
        ioSession.setAttribute(SASL_SERVER_KEY, saslServer);
    }

    /**
     * Handle a received (and decoded) SASL response message.  This method is called when the upper-level
     * protocol receives a complete SASL response message.  If another challenge is produced, it will be sent via the
     * provided {@link org.apache.mina.filter.sasl.SaslMessageSender}.
     *
     * @param ioSession the session
     * @param response the received response data
     *
     * @return {@code true} if authentication is complete (no more responses are needed from the client)
     *
     * @throws IOException if an error occurs during processing of the message, or during the transmission of the next challenge
     */
    public boolean handleSaslResponse(IoSession ioSession, byte[] response) throws IOException {
        final SaslServer server = getSaslServer(ioSession);
        final byte[] challenge = server.evaluateResponse(response);
        if (challenge != null) {
            sendSaslMessage(ioSession, challenge);
        }
        return server.isComplete();
    }

    /**
     * Send an initial challenge.  Useful for protocols where authentication is initiated by the server (rather than
     * the client).
     *
     * @param ioSession the session
     *
     * @return {@code true} if authentication is complete (no more responses are needed from the client)
     *
     * @throws IOException if an error occurs during processing of the message, or during the transmission of the next challenge
     */
    public boolean sendInitialChallenge(IoSession ioSession) throws IOException {
        return handleSaslResponse(ioSession, new byte[0]);
    }

    /**
     * Determine whether SASL negotiation is complete for a session.
     *
     * @param ioSession the session
     * @return {@code true} if negotiation is complete
     *
     * @throws IOException if the completeness could not be determined
     *
     * @see javax.security.sasl.SaslServer#isComplete()
     */
    public boolean isComplete(IoSession ioSession) throws IOException {
        return getSaslServer(ioSession).isComplete();
    }

    /**
     * Get the quality of protection negotiated by this SASL session.
     *
     * @param ioSession the session
     * @return the negotiated quality of protection
     *
     * @see javax.security.sasl.Sasl#QOP
     */
    protected String getQop(final IoSession ioSession) {
        return (String) getSaslServer(ioSession).getNegotiatedProperty(Sasl.QOP);
    }

    protected byte[] wrap(IoSession ioSession, byte[] data, int offs, int len) throws SaslException {
        return getSaslServer(ioSession).wrap(data, offs, len);
    }

    protected byte[] unwrap(IoSession ioSession, byte[] data, int offs, int len) throws SaslException {
        return getSaslServer(ioSession).unwrap(data, offs, len);
    }
}
