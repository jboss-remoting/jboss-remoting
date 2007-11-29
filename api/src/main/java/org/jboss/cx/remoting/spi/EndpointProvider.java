package org.jboss.cx.remoting.spi;

import java.util.concurrent.ExecutorService;
import org.jboss.cx.remoting.Endpoint;

/**
 *
 */
public interface EndpointProvider {
    Endpoint createEndpoint(String name);
}
