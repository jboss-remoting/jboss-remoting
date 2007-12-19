package org.jboss.cx.remoting.spi.wrapper;

import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.ContextSource;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.Session;

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

    public String getEndpointName() {
        return delegate.getEndpointName();
    }

    public <I, O> ContextSource<I, O> openService(final ServiceLocator<I, O> locator) throws RemotingException {
        return delegate.openService(locator);
    }
}
