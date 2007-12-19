package org.jboss.cx.remoting.spi;

import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 * A simple base implementation of {@code ContextServiceInterceptor}.  Use this class as a base for simple
 * implementations of that interface.
 */
public abstract class AbstractClientInterceptor implements ClientInterceptor {
    protected final Context<?, ?> context;
    protected ClientInterceptor next, prev;

    protected AbstractClientInterceptor(final Context<?, ?> context) {
        this.context = context;
    }

    public final void setNext(final ClientInterceptor next) {
        this.next = next;
    }

    public final void setPrevious(final ClientInterceptor prev) {
        this.prev = prev;
    }

    public void processOutboundRequest(final InterceptorContext context, final RequestIdentifier requestIdentifier, final Request<?> request) {
        next.processOutboundRequest(context, requestIdentifier, request);
    }

    public void processInboundReply(final InterceptorContext context, final RequestIdentifier requestIdentifier, final Reply<?> reply) {
        prev.processInboundReply(context, requestIdentifier, reply);
    }

    public void processInboundException(final InterceptorContext context, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) {
        prev.processInboundException(context, requestIdentifier, exception);
    }

    public <T extends ContextService> T getContextService(InterceptorContext context) {
        return null;
    }

    public void processOutboundCancelRequest(final InterceptorContext context, final RequestIdentifier requestIdentifier, final boolean mayInterrupt) {
        next.processOutboundCancelRequest(context, requestIdentifier, true);
    }

    public void processInboundCancelAcknowledge(final InterceptorContext context, final RequestIdentifier requestIdentifier) {
        prev.processInboundCancelAcknowledge(context, requestIdentifier);
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
