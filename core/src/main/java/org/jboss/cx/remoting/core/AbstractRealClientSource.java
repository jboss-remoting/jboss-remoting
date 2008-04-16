package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.ClientSource;

/**
 *
 */
public abstract class AbstractRealClientSource<I, O> implements ClientSource<I, O> {
    private ServiceResponder<I, O> serviceResponder;

    protected AbstractRealClientSource(final ServiceResponder<I, O> serviceResponder) {
        if (serviceResponder == null) {
            throw new NullPointerException("serviceResponder is null");
        }
        this.serviceResponder = serviceResponder;
    }

    public ServiceResponder<I, O> getServiceServer() {
        return serviceResponder;
    }
}
