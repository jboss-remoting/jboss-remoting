package org.jboss.remoting.core.security.sasl;

import java.util.Map;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslClientFactory;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

/**
 *
 */
public final class NullSaslFactoryImpl implements SaslServerFactory, SaslClientFactory {
    public SaslServer createSaslServer(String mechanism, String protocol, String serverName, Map<String, ?> props, CallbackHandler cbh) throws SaslException {
        return isNotValidForUs(props) ? null : new NullSaslServerImpl();
    }

    public SaslClient createSaslClient(String[] mechanisms, String authorizationId, String protocol, String serverName, Map<String, ?> props, CallbackHandler cbh) throws SaslException {
        return isNotValidForUs(props) ? null : new NullSaslClientImpl();
    }

    public String[] getMechanismNames(Map<String, ?> props) {
        if (isNotValidForUs(props)) {
            return new String[0];
        } else {
            return new String[] { "NULL" };
        }
    }

    private boolean isNotValidForUs(final Map<String, ?> props) {
        return "true".equals(props.get(Sasl.POLICY_FORWARD_SECRECY))
                || "true".equals(props.get(Sasl.POLICY_NOACTIVE))
                || "true".equals(props.get(Sasl.POLICY_NOANONYMOUS))
                || "true".equals(props.get(Sasl.POLICY_NODICTIONARY))
                || "true".equals(props.get(Sasl.POLICY_NOPLAINTEXT))
                || "true".equals(props.get(Sasl.POLICY_PASS_CREDENTIALS))
                || props.get(Sasl.QOP) != null
                || "true".equals(props.get(Sasl.SERVER_AUTH));
    }
}
