package org.jboss.cx.remoting.spi;

import org.jboss.cx.remoting.Endpoint;

/**
 *
 */
public interface EndpointProvider {
    Endpoint createEndpoint(String name);
}
