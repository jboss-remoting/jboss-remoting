package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 *
 */
public interface ProtocolContextClient<I, O> extends ContextClient {
    RequestClient<O> addRequest(RequestIdentifier requestIdentifier);

}
