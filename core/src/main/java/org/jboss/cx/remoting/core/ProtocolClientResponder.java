package org.jboss.cx.remoting.core;

/**
 *
 */
public interface ProtocolClientResponder<I, O> extends ClientResponder<I, O> {
    RequestInitiator<O> getRequestClient(RequestIdentifier requestIdentifier);
}
