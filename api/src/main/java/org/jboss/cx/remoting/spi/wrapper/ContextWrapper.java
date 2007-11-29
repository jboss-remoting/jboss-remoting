package org.jboss.cx.remoting.spi.wrapper;

import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.FutureReply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.spi.ContextService;

/**
 *
 */
public class ContextWrapper<I, O> implements Context<I, O> {
    protected final Context<I, O> delegate;

    protected ContextWrapper(final Context<I, O> delegate) {
        this.delegate = delegate;
    }

    public void close() throws RemotingException {
        delegate.close();
    }

    public Request<I> createRequest(final I body) {
        return delegate.createRequest(body);
    }

    public Reply<O> invoke(final Request<I> request) throws RemotingException, RemoteExecutionException, InterruptedException {
        return delegate.invoke(request);
    }

    public FutureReply<O> send(final Request<I> request) throws RemotingException {
        return delegate.send(request);
    }

    public ConcurrentMap<Object, Object> getAttributes() {
        return delegate.getAttributes();
    }

    public <T extends ContextService> T getService(Class<T> serviceType) throws RemotingException {
        return delegate.getService(serviceType);
    }

    public <T extends ContextService> boolean hasService(Class<T> serviceType) {
        return delegate.hasService(serviceType);
    }
}
