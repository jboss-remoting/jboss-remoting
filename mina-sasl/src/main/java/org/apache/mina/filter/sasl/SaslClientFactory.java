package org.apache.mina.filter.sasl;

import org.apache.mina.common.IoSession;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public interface SaslClientFactory {
    SaslClient createSaslClient(IoSession ioSession, CallbackHandler callbackHandler) throws SaslException;
}
