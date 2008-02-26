package org.jboss.cx.remoting;

import java.util.concurrent.ConcurrentMap;

/**
 * A communications context.  The context may be associated with a security/authentication state and a transactional
 * state, as well as other state maintained by the remote side.
 */
public interface Context<I, O> extends Closeable<Context<I, O>> {

    void close() throws RemotingException;

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
     * @throws InterruptedException if the request was interrupted (and thereby cancelled)
     */
    O invoke(I request) throws RemotingException, RemoteExecutionException, InterruptedException;

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
     * Get the context map.  This map holds metadata about the current context.
     *
     * @return the context map
     */
    ConcurrentMap<Object, Object> getAttributes();

    /**
     * Get a service client interface.  The context must support the service with the given
     * client interface.
     *
     * @param serviceType the service interface type
     * @return an instance of the given interface
     * @throws RemotingException if the service is not valid or is not available
     */
    <T> T getService(Class<T> serviceType) throws RemotingException;

    /**
     * Determine whether this context supports a service with the given client interface.
     *
     * @param serviceType the service interface type
     * @return {@code true} if the given service type is supported
     */
    <T> boolean hasService(Class<T> serviceType);
}
