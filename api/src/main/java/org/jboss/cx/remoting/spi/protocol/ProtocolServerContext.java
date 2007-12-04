package org.jboss.cx.remoting.spi.protocol;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public interface ProtocolServerContext {
    ProtocolContext establishSession(ProtocolHandler handler);

    /**
     * Get the callback handler responsible for authenticating the client side of a potential incoming
     * session establishment.
     *
     * @return the callback handler, or {@code null} to skip client authentication
     */
    CallbackHandler getClientCallbackHandler();

    /**
     * Get the callback handler responsible for authenticating this protocol server to a remote client.
     *
     * @return the callback handler, or {@code null} to skip server authentication
     */
    CallbackHandler getServerCallbackHandler();

}
