package org.jboss.cx.remoting.deployer;

import org.jboss.kernel.plugins.deployment.AbstractKernelDeployment;
import org.jboss.cx.remoting.Endpoint;

/**
 *
 */
public final class EndpointKernelDeployment extends AbstractKernelDeployment {
    private Endpoint endpoint;

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }
}
