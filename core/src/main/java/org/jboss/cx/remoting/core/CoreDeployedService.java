package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RequestListener;

/**
 *
 */
public final class CoreDeployedService<I, O> {
    private final String name;
    private final String type;
    private final RequestListener<I, O> requestListener;

    CoreDeployedService(final String name, final String type, final RequestListener<I, O> requestListener) {
        this.name = name;
        this.type = type;
        this.requestListener = requestListener;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public RequestListener<I, O> getRequestListener() {
        return requestListener;
    }
}
