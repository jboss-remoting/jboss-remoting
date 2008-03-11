package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.Context;
import java.io.Serializable;

/**
 *
 */
public abstract class AbstractRealContext<I, O> implements Context<I, O>, Serializable {
    private static final long serialVersionUID = 1L;

    private ContextServer<I,O> contextServer;

    protected AbstractRealContext(final ContextServer<I, O> contextServer) {
        this.contextServer = contextServer;
    }

    private Object writeReplace() {
        return contextServer;
    }

    protected ContextServer<I, O> getContextServer() {
        return contextServer;
    }
}
