package org.jboss.cx.remoting;

/**
 *
 */
public interface RequestListener<I, O> {
    /**
     * Handle the opening of a context.
     */
    void handleOpen();

    /**
     * Handle a request.  If this method throws {@code RemoteExecutionException}, then that exception is passed
     * back to the caller and the request is marked as complete.  If this method throws {@code InterruptedException},
     * the request is cancelled, and the interrupted status is propagated to the executor..  Otherwise, the request
     * listener must send back either a reply (using the {@code sendReply()} method on the {@code RequestContext}) or
     * an exception (using the {@code sendException()} method on the {@code RequestContext}).  Failure to do so may
     * cause the client to hang indefinitely.
     *
     * @param context the context on which a reply may be sent
     * @param request the received request
     *
     * @throws RemoteExecutionException if the execution failed in some way
     * @throws InterruptedException if the thread is interrupted
     */
    void handleRequest(RequestContext<O> context, I request) throws RemoteExecutionException, InterruptedException;

    /**
     * Handle the close of a context.
     */
    void handleClose();
}
