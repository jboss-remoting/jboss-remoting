package org.jboss.cx.remoting.spi.wrapper;

import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.FutureReply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public class ClientWrapper<I, O> implements Client<I, O> {
    protected final Client<I, O> delegate;

    protected ClientWrapper(final Client<I, O> delegate) {
        this.delegate = delegate;
    }

    public void close() throws RemotingException {
        delegate.close();
    }

    public void closeImmediate() throws RemotingException {
        delegate.closeImmediate();
    }

    public void addCloseHandler(final CloseHandler<Client<I, O>> closeHandler) {
        delegate.addCloseHandler(new CloseHandler<Client<I, O>>() {
            public void handleClose(final Client<I, O> closed) {
                closeHandler.handleClose(ClientWrapper.this);
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

    public ClassLoader getClassLoader() {
        return delegate.getClassLoader();
    }
}
