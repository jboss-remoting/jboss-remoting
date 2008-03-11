package org.jboss.cx.remoting.spi.wrapper;

import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.FutureReply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.CloseHandler;

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

    public void closeCancelling(final boolean mayInterrupt) throws RemotingException {
        delegate.closeCancelling(mayInterrupt);
    }

    public void closeImmediate() throws RemotingException {
        delegate.closeImmediate();
    }

    public void addCloseHandler(final CloseHandler<Context<I, O>> closeHandler) {
        delegate.addCloseHandler(new CloseHandler<Context<I, O>>() {
            public void handleClose(final Context<I, O> closed) {
                closeHandler.handleClose(ContextWrapper.this);
            }
        });
    }

    public O invoke(final I request) throws RemotingException, RemoteExecutionException {
        return delegate.invoke(request);
    }

    public FutureReply<O> send(final I request) throws RemotingException {
        return delegate.send(request);
    }

    public void sendOneWay(final I request) throws RemotingException {
        delegate.sendOneWay(request);
    }

    public ConcurrentMap<Object, Object> getAttributes() {
        return delegate.getAttributes();
    }

}
