package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 *
 */
public interface ProtocolClientInitiator<I, O> extends ClientInitiator {
    RequestInitiator<O> addRequest(RequestIdentifier requestIdentifier);

}
