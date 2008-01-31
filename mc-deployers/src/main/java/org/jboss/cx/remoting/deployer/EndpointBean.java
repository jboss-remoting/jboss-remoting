package org.jboss.cx.remoting.deployer;

import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.spi.EndpointProvider;

/**
 *
 */
public final class EndpointBean {
    private EndpointProvider provider;
    private Endpoint endpoint;
    private String endpointName;

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public EndpointProvider getProvider() {
        return provider;
    }

    public void setProvider(final EndpointProvider provider) {
        this.provider = provider;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public void setEndpointName(final String endpointName) {
        this.endpointName = endpointName;
    }

    public void start() {
        endpoint = provider.createEndpoint(endpointName);
    }

    public void stop() {
        try {
            endpoint.shutdown();
        } finally {
            endpoint = null;
        }
    }
}
