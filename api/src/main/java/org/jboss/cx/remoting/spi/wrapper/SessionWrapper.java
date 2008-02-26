package org.jboss.cx.remoting.spi.wrapper;

import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.Context;

/**
 *
 */
public class SessionWrapper implements Session {
    protected final Session delegate;

    protected SessionWrapper(final Session delegate) {
        this.delegate = delegate;
    }

    public void close() throws RemotingException {
        delegate.close();
    }

    public ConcurrentMap<Object, Object> getAttributes() {
        return delegate.getAttributes();
    }

    public String getLocalEndpointName() {
        return delegate.getLocalEndpointName();
    }

    public String getRemoteEndpointName() {
        return delegate.getRemoteEndpointName();
    }

    public <I, O> Context<I, O> getRootContext() {
        return delegate.getRootContext();
    }
}
