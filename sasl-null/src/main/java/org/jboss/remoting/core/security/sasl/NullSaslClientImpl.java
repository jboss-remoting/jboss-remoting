package org.jboss.remoting.core.security.sasl;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

/**
 *
 */
public final class NullSaslClientImpl implements SaslClient {


    public String getMechanismName() {
        return "NULL";
    }

    public boolean hasInitialResponse() {
        return false;
    }

    public byte[] evaluateChallenge(byte[] challenge) throws SaslException {
        return null;
    }

    public boolean isComplete() {
        return true;
    }

    public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException {
        throw new IllegalStateException("unwrap()");
    }

    public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException {
        throw new IllegalStateException("wrap()");
    }

    public Object getNegotiatedProperty(String propName) {
        return null;
    }

    public void dispose() throws SaslException {
    }
}
