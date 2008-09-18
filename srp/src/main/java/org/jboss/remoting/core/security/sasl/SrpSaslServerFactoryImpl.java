package org.jboss.remoting.core.security.sasl;

import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

/**
 *
 */
public final class SrpSaslServerFactoryImpl implements SaslServerFactory {
    public SaslServer createSaslServer(String mechanism, String protocol, String serverName, Map<String, ?> props, CallbackHandler cbh) throws SaslException {
        return new SrpSaslServerImpl(cbh, props);
    }

    public String[] getMechanismNames(Map<String, ?> props) {
        return new String[] { "SRP" };
    }
}
