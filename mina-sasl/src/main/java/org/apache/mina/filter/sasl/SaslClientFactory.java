package org.apache.mina.filter.sasl;

import org.apache.mina.common.IoSession;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

/**
 *
 */
public interface SaslClientFactory {
    SaslClient createSaslClient(IoSession ioSession) throws SaslException;
}
