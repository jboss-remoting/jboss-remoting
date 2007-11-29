package org.jboss.cx.remoting.core.security.sasl;

import java.util.Map;

import javax.security.sasl.SaslServerFactory;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslException;
import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public final class SrpSaslServerFactoryImpl implements SaslServerFactory {
    public SaslServer createSaslServer(String mechanism, String protocol, String serverName, Map<String, ?> props, CallbackHandler cbh) throws SaslException {
        return new SrpSaslServerImpl(cbh, props);
    }

    public String[] getMechanismNames(Map<String, ?> props) {
        return new String[0];
    }
}
