package org.apache.mina.filter.sasl;

import java.io.IOException;
import org.apache.mina.common.IoSession;

/**
 *
 */
public interface SaslMessageSender {
    /**
     * Send a SASL challenge or response message.  This method will take the raw
     * message data and encode it into a protocol-specific form, and send it to the
     * remote side.
     *
     * @param ioSession the {@code IoSession} that the message should be sent over
     * @param rawMsgData the raw SASL data
     *
     * @throws IOException if an error occurs
     */
    void sendSaslMessage(IoSession ioSession, byte[] rawMsgData) throws IOException;
}
