package org.jboss.cx.remoting.deployer;

import org.jboss.cx.remoting.spi.EndpointProvider;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.deployers.spi.deployer.helpers.AbstractSimpleRealDeployer;
import org.jboss.deployers.spi.DeploymentException;
import org.jboss.deployers.structure.spi.DeploymentUnit;

/**
 * This is the real endpoint deployer.  It takes an endpoint deployment and deployes it.
 */
public final class EndpointDeployer extends AbstractSimpleRealDeployer<EndpointKernelDeployment> {
    private static final Logger log = Logger.getLogger(EndpointDeployer.class);

    private EndpointProvider provider;

    public EndpointDeployer() {
        super(EndpointKernelDeployment.class);
    }

    public EndpointProvider getProvider() {
        return provider;
    }

    public void setProvider(final EndpointProvider provider) {
        log.debug("Setting endpoint provider to %s", provider);
        this.provider = provider;
    }

    public void deploy(final DeploymentUnit deploymentUnit, final EndpointKernelDeployment kernelDeployment) throws DeploymentException {
    }
}
