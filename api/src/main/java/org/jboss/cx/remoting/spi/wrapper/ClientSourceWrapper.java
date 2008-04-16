package org.jboss.cx.remoting.spi.wrapper;

import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.ClientSource;
import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public class ClientSourceWrapper<I, O> implements ClientSource<I, O> {
    private final ClientSource<I, O> delegate;

    protected ClientSourceWrapper(ClientSource<I, O> delegate) {
        this.delegate = delegate;
    }

    public void close() throws RemotingException {
        delegate.close();
    }

    public void closeImmediate() throws RemotingException {
        delegate.closeImmediate();
    }

    public void addCloseHandler(final CloseHandler<ClientSource<I, O>> closeHandler) {
        delegate.addCloseHandler(new CloseHandler<ClientSource<I, O>>() {
            public void handleClose(final ClientSource<I, O> closed) {
                closeHandler.handleClose(ClientSourceWrapper.this);
            }
        });
    }

    public Client<I, O> createContext() throws RemotingException {
        return delegate.createContext();
    }
}
