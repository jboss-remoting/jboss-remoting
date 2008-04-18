package org.jboss.cx.remoting.spi.protocol;

import java.io.IOException;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.util.ByteMessageInput;
import org.jboss.cx.remoting.util.ByteMessageOutput;
import org.jboss.cx.remoting.util.ObjectMessageInput;
import org.jboss.cx.remoting.util.ObjectMessageOutput;

/**
 * The receiver interface for protocol sessions.  Methods on this interface are invoked as a result of
 * the corresponding methods in {@link org.jboss.cx.remoting.spi.protocol.ProtocolHandler} being called on the
 * remote side.
 *
 * These methods generally do not throw exceptions, in order to simplify protocol implementation.
 */
public interface ProtocolContext {

    /* CLIENT methods */

    /**
     * Receive a reply to a request.
     *
     * @param clientIdentifier the client identifier
     * @param requestIdentifier the identifier of the request that was finished
     * @param reply the reply
     */
    void receiveReply(ClientIdentifier clientIdentifier, RequestIdentifier requestIdentifier, Object reply);

    /**
     * Receive an exception response to a request.
     *
     * @param clientIdentifier the client identifier
     * @param requestIdentifier the identifier of the request which failed
     * @param exception the exception
     */
    void receiveException(ClientIdentifier clientIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception);

    /**
     * Receive a cancel acknowledgement to a request.
     *
     * @param clientIdentifier the client identifier
     * @param requestIdentifier the identifier of the request which was cancelled
     */
    void receiveCancelAcknowledge(ClientIdentifier clientIdentifier, RequestIdentifier requestIdentifier);

    /**
     * Receive a notification that the given service is closing - that is, no more clients can be opened from it.
     *
     * @param serviceIdentifier the identifier of the closing service
     */
    void receiveServiceClosing(ServiceIdentifier serviceIdentifier);

    /**
     * Receive a notification that the given client is closing - that is, no more requests may be sent on it.
     *
     * @param clientIdentifier the identifier of the closing client
     * @param done {@code true} if the client is fully closed
     */
    void receiveClientClosing(ClientIdentifier clientIdentifier, boolean done);

    /* SERVER methods */

    /**
     * Receive a notification that the given service was closed.  The close may have been caused by a call to the
     * {@link org.jboss.cx.remoting.ClientSource#close()} method.
     *
     * @param remoteServiceIdentifier the service identifier
     */
    void receiveServiceClose(ServiceIdentifier remoteServiceIdentifier);

    /**
     * Receive a notification that the given client was closed.  The close may have been caused by a call to the
     * {@link org.jboss.cx.remoting.Client#close()} or {@link org.jboss.cx.remoting.Client#closeImmediate()} methods.
     *
     * @param remoteClientIdentifier the client identifier
     * @param immediate {@code true} if the client should be closed immediately
     * @param cancel {@code true} if outstanding requests should be cancelled
     * @param interrupt {@code true} if outstanding requests should be interrupted
     */
    void receiveClientClose(ClientIdentifier remoteClientIdentifier, boolean immediate, boolean cancel, boolean interrupt);

    /**
     * Receive a notification that a context was opened within a service.  Subsequent requests for this client will
     * be associated with the service.  This method is idempotent, meaning that multiple calls with the same parameters
     * are treated as one call.
     *
     * @param remoteServiceIdentifier the service identifier
     * @param remoteClientIdentifier the client identifier
     */
    void receiveOpenedContext(ServiceIdentifier remoteServiceIdentifier, ClientIdentifier remoteClientIdentifier);

    /**
     * Receive a request from the remote side.
     *
     * @param remoteClientIdentifier the client identifier
     * @param requestIdentifier the request identifier
     * @param request the request
     */
    void receiveRequest(ClientIdentifier remoteClientIdentifier, RequestIdentifier requestIdentifier, Object request);

    /**
     * Receive a request to cancel an outstanding request.
     *
     * @param remoteClientIdentifier the client identifier
     * @param requestIdentifier the request identifier
     * @param mayInterrupt {@code true} if the request may be interrupted
     */
    void receiveCancelRequest(ClientIdentifier remoteClientIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt);

    /* SESSION methods */

    void closeStream(StreamIdentifier streamIdentifier);

    void receiveStreamData(StreamIdentifier streamIdentifier, ObjectMessageInput data);

    void receiveRemoteSideReady(String remoteEndpointName);

    void closeSession();

    /* CLIENT OR SERVER methods */

    ObjectMessageOutput getMessageOutput(ByteMessageOutput target) throws IOException;

    ObjectMessageOutput getMessageOutput(ByteMessageOutput target, Executor streamExecutor) throws IOException;

    ObjectMessageInput getMessageInput(ByteMessageInput source) throws IOException;

    String getLocalEndpointName();
}
