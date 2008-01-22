package org.jboss.cx.remoting;

/**
 * The context of a single request.
 */
public interface RequestContext<O> {
    /**
     * Determine whether the current request was cancelled.
     *
     * @return {@code true} if the request was cancelled
     */
    boolean isCancelled();

    /**
     * Send a reply back to the caller.
     *
     * @param reply the reply to send
     * @throws RemotingException if the transmission failed
     * @throws IllegalStateException if a reply was already sent
     */
    void sendReply(O reply) throws RemotingException, IllegalStateException;

    /**
     * Send a failure message back to the caller.
     *
     * @param msg a message describing the failure, if any (can be {@code null})
     * @param cause the failure cause, if any (can be {@code null})
     *
     * @throws RemotingException if the transmission failed
     * @throws IllegalStateException if a reply was already sent
     */
    void sendFailure(String msg, Throwable cause) throws RemotingException, IllegalStateException;

    /**
     * Send a cancellation message back to the client.
     *
     * @throws RemotingException if the message could not be sent (the client could not be notified about the cancellation)
     * @throws IllegalStateException if a reply was already sent
     */
    void sendCancelled() throws RemotingException, IllegalStateException;

    /**
     * Set a notifier to be called if a cancel request is received.  The notifier may be called from the current thread
     * or a different thread.  If the request has already been cancelled, the notifier will be called immediately.  If there
     * was a previous notifier set, it will be overwritten.  Calling this method guarantees that the supplied handler
     * will be called, unless it is cleared or a new handler is set before the original handler is called.  The handler
     * may be called at any time after the cancel request is recevied, though implementations should make a reasonable effort
     * to ensure that the handler is called in a timely manner.  The handler is cleared before it is called.
     * <p/>
     * Setting a {@code null} value clears any notifier.
     * <p/>
     * This method returns {@code this} in order to facilitate method call chaining.
     *
     * @param handler
     */
    void setCancelHandler(RequestCancelHandler<O> handler);
}
