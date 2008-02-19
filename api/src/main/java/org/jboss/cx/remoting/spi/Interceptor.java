package org.jboss.cx.remoting.spi;

import org.jboss.cx.remoting.RemoteExecutionException;

/**
 *
 */
public interface Interceptor {
    /**
     * Process a request.
     *
     * @param context the context service interceptor context
     * @param request the outbound request
     */
    void processRequest(InterceptorContext context, Object request);

    /**
     * Process a request reply.
     *
     * @param context the context service interceptor context
     * @param reply the inbound reply
     */
    void processReply(InterceptorContext context, Object reply);

    /**
     * Process a request exception.
     *
     * @param context the context service interceptor context
     * @param exception the inbound exception
     */
    void processException(InterceptorContext context, RemoteExecutionException exception);

    /**
     * Process a cancellation request.
     *
     * @param context the context service interceptor context
     * @param mayInterrupt {@code true} if the operation can be interrupted
     */
    void processCancelRequest(InterceptorContext context, boolean mayInterrupt);

    /**
     * Process a cancellation acknowledgement.
     *
     * @param context the context service interceptor context
     */
    void processCancelAcknowledge(InterceptorContext context);

    /**
     * Close this interceptor.
     */
    void close();
}
