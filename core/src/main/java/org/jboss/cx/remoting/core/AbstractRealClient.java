package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.Client;

/**
 *
 */
public abstract class AbstractRealClient<I, O> implements Client<I, O> {

    private ClientResponder<I,O> clientResponder;
    private ClassLoader classLoader;

    protected AbstractRealClient(final ClientResponder<I, O> clientResponder, final ClassLoader classLoader) {
        if (clientResponder == null) {
            throw new NullPointerException("clientResponder is null");
        }
        if (classLoader == null) {
            throw new NullPointerException("classLoader is null");
        }
        this.clientResponder = clientResponder;
        this.classLoader = classLoader;
    }

    protected ClientResponder<I, O> getContextServer() {
        return clientResponder;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }
}
