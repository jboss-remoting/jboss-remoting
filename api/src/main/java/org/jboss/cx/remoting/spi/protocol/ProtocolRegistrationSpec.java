package org.jboss.cx.remoting.spi.protocol;

import org.jboss.cx.remoting.core.util.BufferFactory;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public final class ProtocolRegistrationSpec {
    private final String scheme;
    private final ProtocolHandlerFactory protocolHandlerFactory;

    public static final ProtocolRegistrationSpec DEFAULT = new ProtocolRegistrationSpec(null, null);

    private ProtocolRegistrationSpec(final String scheme, final ProtocolHandlerFactory protocolHandlerFactory) {
        this.scheme = scheme;
        this.protocolHandlerFactory = protocolHandlerFactory;
    }

    public String getScheme() {
        return scheme;
    }

    public ProtocolHandlerFactory getProtocolHandlerFactory() {
        return protocolHandlerFactory;
    }

    public ProtocolRegistrationSpec setScheme(String scheme) {
        return new ProtocolRegistrationSpec(scheme, protocolHandlerFactory);
    }

    public ProtocolRegistrationSpec setProtocolHandlerFactory(ProtocolHandlerFactory protocolHandlerFactory) {
        return new ProtocolRegistrationSpec(scheme, protocolHandlerFactory);
    }
}

