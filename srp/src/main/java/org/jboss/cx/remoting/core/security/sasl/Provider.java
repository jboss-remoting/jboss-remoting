package org.jboss.cx.remoting.core.security.sasl;

/**
 *
 */
public final class Provider extends java.security.Provider {

    private static final long serialVersionUID = 1L;

    public Provider() {
        super("JBossRemotingSASL", 3.0, "JBoss Remoting simple default SASL provider (provides SRP mechanism)");
        put("SaslClientFactory.SRP", "org.jboss.cx.remoting.core.security.sasl.SrpSaslClientFactoryImpl");
        put("SaslServerFactory.SRP", "org.jboss.cx.remoting.core.security.sasl.SrpSaslServerFactoryImpl");
    }
}
