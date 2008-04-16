package org.jboss.cx.remoting.spi.wrapper;

import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestCancelHandler;
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.ClientContext;

/**
 *
 */
public class RequestContextWrapper<O> implements RequestContext<O> {
    protected final RequestContext<O> delegate;

    protected RequestContextWrapper(final RequestContext<O> delegate) {
        this.delegate = delegate;
    }

    public ClientContext getContext() {
        return delegate.getContext();
    }

    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    public void sendReply(O reply) throws RemotingException, IllegalStateException {
        delegate.sendReply(reply);
    }

    public void sendFailure(String msg, Throwable cause) throws RemotingException, IllegalStateException {
        delegate.sendFailure(msg, cause);
    }

    public void sendCancelled() throws RemotingException, IllegalStateException {
        delegate.sendCancelled();
    }

    public void addCancelHandler(final RequestCancelHandler<O> requestCancelHandler) {
        delegate.addCancelHandler(requestCancelHandler);
    }

    public void execute(final Runnable command) {
        delegate.execute(command);
    }
}
