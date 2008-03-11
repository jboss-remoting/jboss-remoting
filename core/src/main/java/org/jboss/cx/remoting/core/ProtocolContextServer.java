package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 *
 */
public interface ProtocolContextServer<I, O> extends ContextServer<I, O> {
    RequestClient<O> getRequestClient(RequestIdentifier requestIdentifier);
}
