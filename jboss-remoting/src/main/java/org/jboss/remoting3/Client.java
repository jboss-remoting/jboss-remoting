package org.jboss.remoting3;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentMap;
import org.jboss.xnio.IoFuture;

/**
 * A communications client.  The client may be associated with state maintained by the local and/or remote side.
 *
 * @param <I> the request type
 * @param <O> the reply type
 *
 * @apiviz.landmark
 */
public interface Client<I, O> extends HandleableCloseable<Client<I, O>> {
    /**
     * Send a request and block until a reply is received.  If the remote side manipulates a stream, the
     * current thread may be used to handle it.
     * <p/>
     * If the operation is cancelled asynchronously, a {@code CancellationException} is thrown.  This exception indicates
     * that the request was received and was executed, but a cancellation request was received and handled before the
     * reply was able to be sent.  The remote service will have cleanly cancelled the operation.  This exception type
     * is a {@code RuntimeException}; thus direct handling of this exception is optional (depending on your use case).
     * <p/>
     * If the request is sent but the remote side sends an exception back, a {@code RemoteExecutionException} is thrown
     * with the cause and message initialized by the remote service.  This exception indicates an error in the execution
     * of the service's {@code RequestListener}.  The service will have cleanly recovered from such an exception.
     * <p/>
     * If the request is sent and the remote side tries to reply, but sending the reply fails, a
     * {@code RemoteReplyException} is thrown, possibly with the cause initialized to the reason of the failure.  Typically
     * this exception is thrown when serialization of the reply failed for some reason.  This exception type extends
     * {@code RemoteExecutionException} and can be treated similarly in most cases.
     * <p/>
     * If the request is sent and the remote side sends the reply successfully but there is an error reading the reply
     * locally, a {@code ReplyException} is thrown.  In this case the operation is known to have completed without error
     * but the actual detailed reply cannot be known.  In cases where the reply would be ignored anyway, this exception
     * type may be safely ignored (possibly logging it for informational purposes).  This exception is typically caused
     * by an {@code ObjectStreamException} thrown while unmarshalling the reply, though other causes are also possible.
     * <p/>
     * If the result of the operation is known to be impossible to ascertain, then an {@code IndeterminateOutcomeException}
     * is thrown.  Possible causes of this condition include (but are not limited to) the connection to the remote side
     * being unexpectedly broken, or the current thread being interrupted before the reply can be read.  In the latter
     * case, a best effort is automatically made to attempt to cancel the outstanding operation, though there is no
     * guarantee.
     * <p/>
     * If the request cannot be sent, some other {@code IOException} will be thrown with the reason, including (but not limited to)
     * attempting to call this method on a closed client, or {@code ObjectStreamException}s related to marshalling the
     * request locally or unmarshalling it remotely.  Such an exception indicates that the remote side did not receive
     * the request.
     * <p/>
     * All these exceptions (apart from {@code CancellationException}) extend {@code IOException} which makes it easier
     * to selectively catch only those exceptions that you need to implement special policy for, while relegating the
     * rest to common handlers.
     *
     * @param request the request to send
     *
     * @return the result of the request
     *
     * @throws CancellationException if the operation was cancelled asynchronously
     * @throws RemoteExecutionException if the remote handler threw an exception
     * @throws RemoteReplyException if the remote side was unable to send the response
     * @throws ReplyException if the operation succeeded but the reply cannot be read for some reason
     * @throws IndeterminateOutcomeException if the result of the operation cannot be ascertained
     * @throws ObjectStreamException if marshalling or unmarshalling some part of the request failed
     * @throws IOException if some I/O error occurred while sending the request
     */
    O invoke(I request) throws IOException, CancellationException;

    /**
     * Send a request asynchronously.  If the remote side manipulates a stream, it
     * may use a local policy to assign one or more thread(s) to handle the local end of that stream, or it may
     * fail with an exception (e.g. if this method is called on a client with no threads to handle streaming).
     * <p/>
     * Returns immediately.  The returned {@code IoFuture} object can be queried at a later time to determine the result
     * of the operation.  If the operation fails, one of the conditions described on the {@link #invoke(Object) invoke(I)}
     * method will result.  This condition can be determined by reading the status of the {@code IoFuture} object or
     * by attempting to read the result.
     *
     * @param request the request to send
     *
     * @return a future representing the result of the request
     *
     * @throws IOException if the request could not be sent
     */
    IoFuture<? extends O> send(I request) throws IOException;

    /**
     * Get the attribute map.  This map holds metadata about the current clinet.
     *
     * @return the attribute map
     */
    ConcurrentMap<Object, Object> getAttributes();
}
