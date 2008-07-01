package org.jboss.cx.remoting;

/**
 * A handler for request listeners to receive a notification when a request was cancelled.
 *
 * @param <O> the reply type
 */
public interface RequestCancelHandler<O> {

    /**
     * Receive notification that the request was cancelled.
     *
     * @param requestContext the request context
     * @param mayInterrupt the value of the cancellation {@code mayInterrupt} flag
     */
    void notifyCancel(RequestContext<O> requestContext, boolean mayInterrupt);
}
