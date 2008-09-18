package org.jboss.remoting;

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
     */
    void notifyCancel(RequestContext<O> requestContext);
}
