package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 *
 */
public interface ProtocolClientResponder<I, O> extends ClientResponder<I, O> {
    RequestInitiator<O> getRequestClient(RequestIdentifier requestIdentifier);
}
