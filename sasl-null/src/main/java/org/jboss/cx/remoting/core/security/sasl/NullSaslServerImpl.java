package org.jboss.cx.remoting.core.security.sasl;

import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslException;

/**
 *
 */
public final class NullSaslServerImpl implements SaslServer {

    public NullSaslServerImpl() {
    }

    public String getMechanismName() {
        return "NULL";
    }

    public byte[] evaluateResponse(byte[] response) throws SaslException {
        return null;
    }

    public boolean isComplete() {
        return true;
    }

    public String getAuthorizationID() {
        return null;
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
