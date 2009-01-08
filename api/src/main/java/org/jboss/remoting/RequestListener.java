package org.jboss.remoting;

/**
 * A request listener.  Implementations of this interface will reply to client requests.
 *
 * @param <I> the request type
 * @param <O> the reply type
 *
 * @apiviz.landmark
 */
public interface RequestListener<I, O> {
    /**
     * Handle the opening of a client.
     *
     * @param context the client context
     */
    void handleClientOpen(ClientContext context);

    /**
     * Handle the opening of a service.
     *
     * @param context the service context
     */
    void handleServiceOpen(ServiceContext context);

    /**
     * Handle a request.  If this method throws {@code RemoteExecutionException}, then that exception is passed
     * back to the caller and the request is marked as complete.  Otherwise, the request
     * listener must send back either a reply (using the {@code sendReply()} method on the {@code RequestContext}) or
     * an exception (using the {@code sendException()} method on the {@code RequestContext}).  Failure to do so may
     * cause the client to hang indefinitely.
     *
     * @param context the context on which a reply may be sent
     * @param request the received request
     *
     * @throws RemoteExecutionException if the execution failed in some way
     */
    void handleRequest(RequestContext<O> context, I request) throws RemoteExecutionException;

    /**
     * Handle the close of a service.
     *
     * @param context the service context
     */
    void handleServiceClose(ServiceContext context);

    /**
     * Handle the close of a client.
     *
     * @param context the client context
     */
    void handleClientClose(ClientContext context);
}
