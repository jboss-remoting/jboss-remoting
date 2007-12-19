package org.jboss.cx.remoting.spi.protocol;

import org.jboss.cx.remoting.core.util.BufferFactory;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public final class ProtocolRegistrationSpec {
    // todo - recheck some of these fields
    private final String scheme;
    private final ProtocolHandlerFactory protocolHandlerFactory;
    private final CallbackHandler serverCallbackHandler;
    private final CallbackHandler clientCallbackHandler;
    private final BufferFactory bufferFactory;

    public static final ProtocolRegistrationSpec DEFAULT = new ProtocolRegistrationSpec(null, null, null, null, BufferFactory.create(8192, false));

    private ProtocolRegistrationSpec(final String scheme, final ProtocolHandlerFactory protocolHandlerFactory, final CallbackHandler serverCallbackHandler, final CallbackHandler clientCallbackHandler, final BufferFactory bufferFactory) {
        this.scheme = scheme;
        this.protocolHandlerFactory = protocolHandlerFactory;
        this.serverCallbackHandler = serverCallbackHandler;
        this.clientCallbackHandler = clientCallbackHandler;
        this.bufferFactory = bufferFactory;
    }

    public String getScheme() {
        return scheme;
    }

    public ProtocolHandlerFactory getProtocolHandlerFactory() {
        return protocolHandlerFactory;
    }

    public CallbackHandler getServerCallbackHandler() {
        return serverCallbackHandler;
    }

    public CallbackHandler getClientCallbackHandler() {
        return clientCallbackHandler;
    }

    public BufferFactory getBufferFactory() {
        return bufferFactory;
    }

    public ProtocolRegistrationSpec setScheme(String scheme) {
        return new ProtocolRegistrationSpec(scheme, protocolHandlerFactory, serverCallbackHandler, clientCallbackHandler, bufferFactory);
    }

    public ProtocolRegistrationSpec setProtocolHandlerFactory(ProtocolHandlerFactory protocolHandlerFactory) {
        return new ProtocolRegistrationSpec(scheme, protocolHandlerFactory, serverCallbackHandler, clientCallbackHandler, bufferFactory);
    }

    public ProtocolRegistrationSpec setServerCallbackHandler(CallbackHandler serverCallbackHandler) {
        return new ProtocolRegistrationSpec(scheme, protocolHandlerFactory, serverCallbackHandler, clientCallbackHandler, bufferFactory);
    }

    public ProtocolRegistrationSpec setClientCallbackHandler(CallbackHandler clientCallbackHandler) {
        return new ProtocolRegistrationSpec(scheme, protocolHandlerFactory, serverCallbackHandler, clientCallbackHandler, bufferFactory);
    }

    public ProtocolRegistrationSpec setBufferFactory(BufferFactory bufferFactory) {
        return new ProtocolRegistrationSpec(scheme, protocolHandlerFactory, serverCallbackHandler, clientCallbackHandler, bufferFactory);
    }
}

