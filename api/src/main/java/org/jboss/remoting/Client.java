package org.jboss.remoting;

import java.util.concurrent.ConcurrentMap;
import java.io.IOException;
import org.jboss.xnio.IoFuture;

/**
 * A communications client.  The client may be associated with state maintained by the local and/or remote side.
 *
 * @param <I> the request type
 * @param <O> the reply type
 */
public interface Client<I, O> extends HandleableCloseable<Client<I, O>> {
    /**
     * Send a request and block until a reply is received.
     * <p/>
     * Uses the default invocation policy for handling remote invocations. If the remote side manipulates a stream, the
     * current thread will be used to handle it by default.
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
    O invoke(I request) throws IOException;

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
     * @throws IOException if the request could not be sent
     */
    IoFuture<O> send(I request) throws IOException;

    /**
     * Send a request asynchronously, ignoring the reply.
     * </p>
     * Uses the default invocation policy for handling remote invocations. If the remote side manipulates a stream, it
     * MAY fail with an exception (e.g. if this method is called on a client with no threads to handle streaming).
     * <p/>
     * Returns immediately.
     *
     * @param request the request to send
     * @throws IOException if the request could not be sent
     */
    void sendOneWay(I request) throws IOException;

    /**
     * Get the attribute map.  This map holds metadata about the current clinet.
     *
     * @return the attribute map
     */
    ConcurrentMap<Object, Object> getAttributes();
}
