package org.jboss.cx.remoting.spi.protocol;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.util.MessageOutput;

/**
 * A protocol handler.
 *
 *
 *
 *
 * <b>Packet Ordering</b>
 *
 * <p>The following ordering constraints apply:</p>
 *
 * <ul>
 * <li>All stream data must be in order per-stream</li>
 * <li>Service activate -> Service terminate</li>
 * <li>Service request -> Service close</li>
 * <li>receive opened context -> request</li>
 * <li>Service activate -> reply</li>
 * <li>request -> cancel</li>
 * <li>* -> close session</li>
 * </ul>
 */
public interface ProtocolHandler {

    /* SERVER methods */

    /**
     * Send a service activation response to the remote side.  The service identifier will have been produced by
     * the protocol handler on the remote side.
     *
     * @param remoteServiceIdentifier the remote service identifier
     * @throws IOException if an I/O error occurs
     */
    void sendServiceActivate(ServiceIdentifier remoteServiceIdentifier) throws IOException;

    /**
     * Send the reply to a request.
     *
     * @param remoteContextIdentifier the context that the request was received under
     * @param requestIdentifier the request identifier
     * @param reply the reply to send
     * @throws IOException if an I/O error occurs
     */
    void sendReply(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Object reply) throws IOException;

    /**
     * Send an exception reply to a request.
     *
     * @param remoteContextIdentifier the context that the request was received under
     * @param requestIdentifier the request identifier
     * @param exception the exception to send
     * @throws IOException if an I/O error occurs
     */
    void sendException(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) throws IOException;

    /**
     * Send a notification to the client that a request was cancelled.
     *
     * @param remoteContextIdentifier the context that the request was received under
     * @param requestIdentifier the request identifier
     * @throws IOException if an I/O error occurs
     */
    void sendCancelAcknowledge(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier) throws IOException;

    /**
     * Notify the client side that the service has been abruptly terminated on the server.  A service may be terminated
     * if it is undeployed, the session is being shut down, or if the activation request was not accepted.
     *
     * @param remoteServiceIdentifier the remote service identifier
     * @throws IOException if an I/O error occurs
     */
    void sendServiceTerminate(ServiceIdentifier remoteServiceIdentifier) throws IOException;

    /* CLIENT methods */

    /**
     * Get a new context identifier that will be used to send requests to the remote side.  The service identifier
     * was previously acquired from the {@link #openService()} method.  Should send a message to the remote side such
     * that the {@link ProtocolContext#receiveOpenedContext(ServiceIdentifier, ContextIdentifier)} method is called with
     * the new service and context identifiers.
     *
     * @param serviceIdentifier the service identifier
     * @return a context identifier associated with the given service identifier
     * @throws IOException if an I/O error occurs
     */
    ContextIdentifier openContext(ServiceIdentifier serviceIdentifier) throws IOException;

    /**
     * Close a previously opened context.  The protocol handler should cause the
     * {@link org.jboss.cx.remoting.spi.protocol.ProtocolContext#closeContext(ContextIdentifier)} method to be called
     * on the remote side for this context identifier.
     *
     * @param contextIdentifier
     * @throws IOException if an I/O error occurs
     */
    void closeContext(ContextIdentifier contextIdentifier) throws IOException;

    /**
     * Acquire a new request identifier that will be used to send a request.
     *
     * @param contextIdentifier the context identifier
     * @return the new request identifier
     * @throws IOException if an I/O error occurs
     */
    RequestIdentifier openRequest(ContextIdentifier contextIdentifier) throws IOException;

    /**
     * Get a new service identifier that will be used to request a service from the remote side.
     *
     * @return the new service identifier
     * @throws IOException if an I/O error occurs
     */
    ServiceIdentifier openService() throws IOException;

    /**
     * Send a service activation request to the remote side.  The service identifier will have been obtained from
     * the {@link #openService()} method on this {@code ProtocolHandler}.
     *
     * @param serviceIdentifier the service identifier
     * @param locator the locator for the new service
     * @throws IOException if an I/O error occurs
     */
    void sendServiceRequest(ServiceIdentifier serviceIdentifier, ServiceLocator<?, ?> locator) throws IOException;

    /**
     * Send a notification that the client is no longer using the given service.
     *
     * @param serviceIdentifier the service identifier
     * @throws IOException if an I/O error occurs
     */
    void closeService(ServiceIdentifier serviceIdentifier) throws IOException;

    /**
     * Send a request to the remote side.
     *
     * @param contextIdentifier the context identifier
     * @param requestIdentifier the request identifier
     * @param request the request body
     * @param streamExecutor the executor to use for stream callbacks
     * @throws IOException if an I/O error occurs
     */
    void sendRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Object request, Executor streamExecutor) throws IOException;

    /**
     * Send a request to cancel a previously sent request.
     *
     * @param contextIdentifier the context identifier
     * @param requestIdentifier the request identifier to cancel
     * @param mayInterrupt {@code true} if processing may be interrupted
     * @throws IOException if an I/O error occurs
     */
    void sendCancelRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) throws IOException;

    /* SESSION methods */

    /**
     * Open a stream on this session.  Since either side may open a stream, it is important that the client and
     * server side take precautions to ensure that both the client and server will not initiate the same stream at
     * the same time.
     *
     * @return the identifier of a stream
     * @throws IOException if an I/O error occurs
     */
    StreamIdentifier openStream() throws IOException;

    /**
     * Close a stream on this session.  When a stream is closed from one side, the other side will send back
     * a close message as well.  Only when a close has been sent AND received for a stream, may the stream identifier
     * be released and possibly reused.
     *
     * @param streamIdentifier
     * @throws IOException if an I/O error occurs
     */
    void closeStream(StreamIdentifier streamIdentifier) throws IOException;

    /**
     * Read a stream identifier from a message.
     *
     * @param input
     * @return the new stream identifier
     * @throws IOException if an I/O error occurs
     */
    StreamIdentifier readStreamIdentifier(ObjectInput input) throws IOException;

    /**
     * Write a stream identifier to an object output stream.
     *
     * @param output the output to write to
     * @param identifier the identifier to write
     * @throws IOException if an I/O error occurs
     */
    void writeStreamIdentifier(ObjectOutput output, StreamIdentifier identifier) throws IOException;

    /**
     * Send data over a stream.  Returns a message output buffer that the message is written into.  When the message
     * is fully written, the {@link org.jboss.cx.remoting.util.MessageOutput#commit()} method will be called to perform the transmission.  The
     * supplied executor should be passed in to
     * {@link org.jboss.cx.remoting.spi.protocol.ProtocolContext#getMessageOutput(org.jboss.cx.remoting.util.ByteOutput, java.util.concurrent.Executor)},
     * if that method is used for serialization.
     *
     * @param streamIdentifier the stream to send data on
     * @param streamExecutor the executor that should be used to handle stream data
     * @return a message buffer into which the message can be written
     *
     * @throws IOException if an I/O error occurs
     */
    MessageOutput sendStreamData(StreamIdentifier streamIdentifier, Executor streamExecutor) throws IOException;

    /**
     * Close the session.
     *
     * @throws IOException if an I/O error occurs
     */
    void closeSession() throws IOException;

    /**
     * Get the name of the remote endpoint.
     *
     * @return the remote endpoint name, or {@code null} if the remote endpoint is anonymous
     */
    String getRemoteEndpointName();
}
