package org.jboss.cx.remoting.deployer;

import org.jboss.cx.remoting.ServiceDeploymentSpec;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.spi.Registration;

/**
 *
 */
public final class ServiceBean<I,O> {
    private Class<I> requestType;
    private Class<O> replyType;
    private String serviceGroupName;
    private String serviceType;
    private RequestListener<I,O> requestListener;
    private Endpoint endpoint;
    private Registration registration;

    public Class<I> getRequestType() {
        return requestType;
    }

    public void setRequestType(final Class<I> requestType) {
        this.requestType = requestType;
    }

    public Class<O> getReplyType() {
        return replyType;
    }

    public void setReplyType(final Class<O> replyType) {
        this.replyType = replyType;
    }

    public String getServiceGroupName() {
        return serviceGroupName;
    }

    public void setServiceGroupName(final String serviceGroupName) {
        this.serviceGroupName = serviceGroupName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(final String serviceType) {
        this.serviceType = serviceType;
    }

    public RequestListener<I, O> getRequestListener() {
        return requestListener;
    }

    public void setRequestListener(final RequestListener<I, O> requestListener) {
        this.requestListener = requestListener;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void create() throws RemotingException {
        final ServiceDeploymentSpec<?, ?> deploymentSpec = ServiceDeploymentSpec.DEFAULT
                .setRequestType(requestType)
                .setReplyType(replyType)
                .setServiceGroupName(serviceGroupName)
                .setServiceType(serviceType)
                .setRequestListener(requestListener);
        registration = endpoint.deployService(deploymentSpec);
    }

    public void start() {
        registration.start();
    }

    public void stop() {
        registration.stop();
    }

    public void destroy() {
        registration.unregister();
    }
}
