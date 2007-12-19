package org.jboss.cx.remoting.spi.wrapper;

import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.ContextSource;
import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public class ContextSourceWrapper<I, O> implements ContextSource<I, O> {
    private final ContextSource<I, O> delegate;

    protected ContextSourceWrapper(ContextSource<I, O> delegate) {
        this.delegate = delegate;
    }

    public void close() {
        delegate.close();
    }

    public Context<I, O> createContext() throws RemotingException {
        return delegate.createContext();
    }
}
