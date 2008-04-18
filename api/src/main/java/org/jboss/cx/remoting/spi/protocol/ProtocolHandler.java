package org.jboss.cx.remoting.spi.protocol;

import java.io.IOException;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.util.ObjectMessageOutput;

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
     * Send the reply to a request.
     *
     * @param remoteClientIdentifier the client that the request was received under
     * @param requestIdentifier the request identifier
     * @param reply the reply to send
     * @throws IOException if an I/O error occurs
     */
    void sendReply(ClientIdentifier remoteClientIdentifier, RequestIdentifier requestIdentifier, Object reply) throws IOException;

    /**
     * Send an exception reply to a request.
     *
     * @param remoteClientIdentifier the client that the request was received under
     * @param requestIdentifier the request identifier
     * @param exception the exception to send
     * @throws IOException if an I/O error occurs
     */
    void sendException(ClientIdentifier remoteClientIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) throws IOException;

    /**
     * Send a notification to the client that a request was cancelled.
     *
     * @param remoteClientIdentifier the client that the request was received under
     * @param requestIdentifier the request identifier
     * @throws IOException if an I/O error occurs
     */
    void sendCancelAcknowledge(ClientIdentifier remoteClientIdentifier, RequestIdentifier requestIdentifier) throws IOException;

    /**
     * Notify the client side that the service has been terminated on the server.  A service may be terminated
     * if it is undeployed, the session is being shut down, or if the client side requested the service to close.
     *
     * @param remoteServiceIdentifier the remote service identifier
     * @throws IOException if an I/O error occurs
     */
    void sendServiceClosing(ServiceIdentifier remoteServiceIdentifier) throws IOException;

    /**
     * Notify the client side that the server is closing the client, and thus it will no longer be available for new requests.
     *
     * @param remoteClientIdentifier the remote client identifier
     * @param done {@code true} if the client is closed and no more replies will arrive for it
     * @throws IOException if an I/O error occurs
     */
    void sendClientClosing(ClientIdentifier remoteClientIdentifier, boolean done) throws IOException;

    /* CLIENT methods */

    /**
     * Get the identifier for the root client for this session.  The root client lives as long as the session is up.
     * This identifier is used to invoke the root client listener from the local side to the remote side.
     *
     * @return the identifier for the root client
     * @throws IOException if an I/O error occurs
     */
    ClientIdentifier getLocalRootClientIdentifier();

    /**
     * Get the identifier for the root client for this session.  The root client lives as long as the session is up.
     * This identifier is used to invoke the root client listener from the remote side to the local side.
     *
     * @return the identifier for the root client
     * @throws IOException if an I/O error occurs
     */
    ClientIdentifier getRemoteRootClientIdentifier();

    /**
     * Get a new client identifier.  The service identifier was received from the remote side.  Should send a message
     * to the remote side such that the
     * {@link ProtocolContext#receiveOpenedContext(ServiceIdentifier, ClientIdentifier)} method is called with
     * the service and client identifiers.
     *
     * @param serviceIdentifier the service identifier
     * @return a client identifier associated with the given service identifier
     * @throws IOException if an I/O error occurs
     */
    ClientIdentifier openClient(ServiceIdentifier serviceIdentifier) throws IOException;

    /**
     * Close a previously opened client.  The protocol handler should cause the
     * {@link ProtocolContext#receiveClientClose(ClientIdentifier ,boolean,boolean,boolean)} method to be called
     * on the remote side for this client identifier.
     *
     * @param clientIdentifier the client identifier
     * @param immediate {@code true} to immediately shut down the client and throw out any executing requests (implies {@code cancel} and {@code interrupt})
     * @param cancel {@code true} to cancel any outstanding requests
     * @param interrupt {@code true} to interrupt tasks that are cancelled (ignored unless {@code immediate} or {@code cancel} are {@code true})
     * @throws IOException if an I/O error occurs
     */
    void sendClientClose(ClientIdentifier clientIdentifier, boolean immediate, boolean cancel, boolean interrupt) throws IOException;

    /**
     * Acquire a new request identifier that will be used to send a request.
     *
     * @param clientIdentifier the client identifier
     * @return the new request identifier
     * @throws IOException if an I/O error occurs
     */
    RequestIdentifier openRequest(ClientIdentifier clientIdentifier) throws IOException;

    /**
     * Send a notification that the client is no longer using the given service.
     *
     * @param serviceIdentifier the service identifier
     * @throws IOException if an I/O error occurs
     */
    void sendServiceClose(ServiceIdentifier serviceIdentifier) throws IOException;

    /**
     * Send a request to the remote side.
     *
     * @param clientIdentifier the client identifier
     * @param requestIdentifier the request identifier
     * @param request the request body
     * @param streamExecutor the executor to use for stream callbacks
     * @throws IOException if an I/O error occurs
     */
    void sendRequest(ClientIdentifier clientIdentifier, RequestIdentifier requestIdentifier, Object request, Executor streamExecutor) throws IOException;

    /**
     * Send a request to cancel a previously sent request.
     *
     * @param clientIdentifier the client identifier
     * @param requestIdentifier the request identifier to cancel
     * @param mayInterrupt {@code true} if processing may be interrupted
     * @throws IOException if an I/O error occurs
     */
    void sendCancelRequest(ClientIdentifier clientIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) throws IOException;

    /* SESSION methods */

    /**
     * Open a serviceless client.  The client identifier may be transmitted to the remote side.
     *
     * @return a client identifier
     * @throws IOException if an I/O error occurs
     */
    ClientIdentifier openClient() throws IOException;

    /**
     * Get a new service identifier that may be transmitted to the remote side.
     *
     * @return the new service identifier
     * @throws IOException if an I/O error occurs
     */
    ServiceIdentifier openService() throws IOException;

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
     * Send data over a stream.  Returns a message output buffer that the message is written into.  When the message
     * is fully written, the {@link org.jboss.cx.remoting.util.ObjectMessageOutput#commit()} method will be called to perform
     * the transmission.  The supplied executor should be passed in to
     * {@link org.jboss.cx.remoting.spi.protocol.ProtocolContext#getMessageOutput(org.jboss.cx.remoting.util.ByteMessageOutput ,java.util.concurrent.Executor)},
     * if that method is used for serialization.
     *
     * @param streamIdentifier the stream to send data on
     * @param streamExecutor the executor that should be used to handle stream data
     * @return a message buffer into which the message can be written
     *
     * @throws IOException if an I/O error occurs
     */
    ObjectMessageOutput sendStreamData(StreamIdentifier streamIdentifier, Executor streamExecutor) throws IOException;

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
