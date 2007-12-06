package org.jboss.cx.remoting.spi.wrapper;

import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.RequestContext;

/**
 *
 */
public class RequestContextWrapper<O> implements RequestContext<O> {
    protected final RequestContext<O> delegate;

    protected RequestContextWrapper(final RequestContext<O> delegate) {
        this.delegate = delegate;
    }

    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    public Reply<O> createReply(final O body) {
        return delegate.createReply(body);
    }

    public void sendReply(Reply<O> reply) throws RemotingException, IllegalStateException {
        delegate.sendReply(reply);
    }

    public void sendFailure(String msg, Throwable cause) throws RemotingException, IllegalStateException {
        delegate.sendFailure(msg, cause);
    }

    public void sendCancelled() throws RemotingException, IllegalStateException {
        delegate.sendCancelled();
    }

}
