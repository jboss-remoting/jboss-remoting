package org.jboss.cx.remoting;

import java.util.concurrent.ConcurrentMap;

/**
 * A communications context.  The context may be associated with a security/authentication state and a transactional
 * state, as well as other state maintained by the remote side.
 */
public interface Context<I, O> extends Closeable<Context<I, O>> {
    /**
     * Send a request and block until a reply is received.
     * <p/>
     * Uses the default invocation policy for handling remote invocations. If the remote side manipulates a stream, the
     * current thread MAY be used to handle it.
     * <p/>
     * If the remote session cannot handle the request, a {@code RemotingException} will be thrown.
     *
     * @param request the request to send
     *
     * @return the result of the request
     *
     * @throws RemotingException if the request could not be sent
     * @throws RemoteExecutionException if the remote handler threw an exception
     */
    O invoke(I request) throws RemotingException, RemoteExecutionException;

    /**
     * Send a request asynchronously.
     * <p/>
     * Uses the default invocation policy for handling remote invocations. If the remote side manipulates a stream, it
     * MAY fail with an exception (e.g. if this method is called on a client with no threads to handle streaming).
     * <p/>
     * Returns immediately.
     *
     * @param request the request to send
     *
     * @return a future representing the result of the request
     *
     * @throws RemotingException if the request could not be sent
     */
    FutureReply<O> send(I request) throws RemotingException;

    /**
     * Send a request asynchronously, ignoring the reply.
     * </p>
     * Uses the default invocation policy for handling remote invocations. If the remote side manipulates a stream, it
     * MAY fail with an exception (e.g. if this method is called on a client with no threads to handle streaming).
     * <p/>
     * Returns immediately.
     *
     * @param request the request to send
     * @throws RemotingException if the request could not be sent
     */
    void sendOneWay(I request) throws RemotingException;

    /**
     * Get the context map.  This map holds metadata about the current context.
     *
     * @return the context map
     */
    ConcurrentMap<Object, Object> getAttributes();

    void close() throws RemotingException;

    void closeImmediate() throws RemotingException;

    void addCloseHandler(final CloseHandler<Context<I, O>> closeHandler);
}
