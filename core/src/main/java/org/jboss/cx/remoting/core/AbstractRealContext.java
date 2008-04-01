package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.Context;

/**
 *
 */
public abstract class AbstractRealContext<I, O> implements Context<I, O> {

    private ContextServer<I,O> contextServer;

    protected AbstractRealContext(final ContextServer<I, O> contextServer) {
        if (contextServer == null) {
            throw new NullPointerException("contextServer is null");
        }
        this.contextServer = contextServer;
    }

    protected ContextServer<I, O> getContextServer() {
        return contextServer;
    }
}
