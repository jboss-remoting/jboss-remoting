package org.jboss.cx.remoting.spi.protocol;

import org.jboss.cx.remoting.Context;

/**
 *
 */
public interface ProtocolServerContext {
    <I, O> ProtocolContext establishSession(ProtocolHandler handler, Context<I, O> rootContext);

}
