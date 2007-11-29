package org.jboss.cx.remoting.spi.protocol;

/**
 *
 */
public interface ProtocolServerContext {
    ProtocolContext establishSession(ProtocolHandler handler);
}
