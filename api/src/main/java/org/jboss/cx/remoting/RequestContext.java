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
     * Create a reply for a request.
     *
     * @param body the body of the reply
     *
     * @return the new reply
     */
    Reply<O> createReply(O body);

    /**
     * Send a reply back to the caller.
     *
     * @param reply the reply to send
     * @throws RemotingException if the transmission failed
     * @throws IllegalStateException if a reply was already sent
     */
    void sendReply(Reply<O> reply) throws RemotingException, IllegalStateException;

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
     * Get a context which can be used to send requests back to the calling side.
     *
     * todo - do we really want this symmetry
     *
     * @param requestType
     * @param replyType
     * @return
     */
    <S, R> Context<S, R> getContext(Class<S> requestType, Class<R> replyType);
}
