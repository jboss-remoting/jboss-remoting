package org.jboss.cx.remoting.spi;

import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 * A simple base implementation of {@code ContextServiceInterceptor}.  Use this class as a base for simple
 * implementations of that interface.
 */
public abstract class AbstractServerInterceptor implements ServerInterceptor {
    protected ServerInterceptor next, prev;

    protected AbstractServerInterceptor() {
    }

    public final void setNext(final ServerInterceptor next) {
        this.next = next;
    }

    public final void setPrevious(final ServerInterceptor prev) {
        this.prev = prev;
    }

    public void processInboundCancelRequest(final InterceptorContext context, final RequestIdentifier requestIdentifier, final boolean mayInterruptIfRunning) {
        prev.processInboundCancelRequest(context, requestIdentifier, true);
    }

    public void processOutboundCancelAcknowledge(final InterceptorContext context, final RequestIdentifier requestIdentifier) {
        next.processOutboundCancelAcknowledge(context, requestIdentifier);
    }

    public void processInboundRequest(final InterceptorContext context, final RequestIdentifier requestIdentifier, final Request<?> request) {
        prev.processInboundRequest(context, requestIdentifier, request);
    }

    public void processOutboundReply(final InterceptorContext context, final RequestIdentifier requestIdentifier, final Reply<?> reply) {
        next.processOutboundReply(context, requestIdentifier, reply);
    }

    public void processOutboundException(final InterceptorContext context, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) {
        next.processOutboundException(context, requestIdentifier, exception);
    }

    public final void close() {
        try {
            doClose();
        } catch (RuntimeException ex) {
            // todo - log the exception
            // consume
        } finally {
            next.close();
        }
    }

    /**
     * Actually perform the close operation.  No delegation is necessary.
     */
    protected void doClose() {
        // do nothing by default; user should override
    }
}
