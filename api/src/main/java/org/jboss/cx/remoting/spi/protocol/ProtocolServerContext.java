package org.jboss.cx.remoting.spi.protocol;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public interface ProtocolServerContext {
    ProtocolContext establishSession(ProtocolHandler handler);

}
