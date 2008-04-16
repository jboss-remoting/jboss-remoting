package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.Client;

/**
 *
 */
public abstract class AbstractRealClient<I, O> implements Client<I, O> {

    private ClientResponder<I,O> clientResponder;

    protected AbstractRealClient(final ClientResponder<I, O> clientResponder) {
        if (clientResponder == null) {
            throw new NullPointerException("clientResponder is null");
        }
        this.clientResponder = clientResponder;
    }

    protected ClientResponder<I, O> getContextServer() {
        return clientResponder;
    }
}
