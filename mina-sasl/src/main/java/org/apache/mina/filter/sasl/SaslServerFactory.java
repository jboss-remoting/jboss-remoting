package org.apache.mina.filter.sasl;

import org.apache.mina.common.IoSession;

import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslException;
import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public interface SaslServerFactory {
    SaslServer createSaslServer(IoSession ioSession, CallbackHandler callbackHandler) throws SaslException;
}
