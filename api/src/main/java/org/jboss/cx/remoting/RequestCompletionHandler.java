package org.jboss.cx.remoting;

/**
 * A handler for receiving notification of request completion on the client side.
 *
 * @param <T> the reply type
 */
public interface RequestCompletionHandler<T> {

    /**
     * Receive notification that the request was completed, was cancelled, or has failed.
     *
     * @param futureReply the future result
     */
    void notifyComplete(FutureReply<T> futureReply);
}
