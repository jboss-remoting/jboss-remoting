package org.jboss.cx.remoting.spi;

import org.jboss.cx.remoting.RemoteExecutionException;

/**
 *
 */
public abstract class AbstractInterceptor implements Interceptor {
    protected AbstractInterceptor() {
    }

    public void processRequest(final InterceptorContext context, final Object request) {
        context.nextRequest(request);
    }

    public void processReply(final InterceptorContext context, final Object reply) {
        context.nextReply(reply);
    }

    public void processCancelRequest(final InterceptorContext context, final boolean mayInterrupt) {
        context.nextCancelRequest(mayInterrupt);
    }

    public void processCancelAcknowledge(final InterceptorContext context) {
        context.nextCancelAcknowledge();
    }

    public void processException(final InterceptorContext context, final RemoteExecutionException exception) {
        context.nextException(exception);
    }

    public void close() {
    }
}
