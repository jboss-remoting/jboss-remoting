package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.ContextSource;

/**
 *
 */
public abstract class AbstractRealContextSource<I, O> implements ContextSource<I, O> {
    private ServiceServer<I, O> serviceServer;

    protected AbstractRealContextSource(final ServiceServer<I, O> serviceServer) {
        if (serviceServer == null) {
            throw new NullPointerException("serviceServer is null");
        }
        this.serviceServer = serviceServer;
    }

    public ServiceServer<I, O> getServiceServer() {
        return serviceServer;
    }
}
