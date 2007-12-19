package org.jboss.cx.remoting.spi.protocol;

import java.io.IOException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.ServiceLocator;

/**
 *
 */
public interface ProtocolHandler {

    /* SERVER methods */

    /**
     * Send a service activation response to the remote side.  The service identifier will have been produced by
     * the protocol handler on the remote side.
     *
     * @param remoteServiceIdentifier the remote service identifier
     * @throws IOException if an error occurs
     */
    void sendServiceActivate(ServiceIdentifier remoteServiceIdentifier) throws IOException;

    void sendReply(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply) throws IOException;

    void sendException(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) throws IOException;

    void sendCancelAcknowledge(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier) throws IOException;

    /**
     * Notify the client side that the service has been abruptly terminated on the server.  A service may be terminated
     * if it is undeployed, the session is being shut down, or if the activation request was not accepted.
     *
     * @param remoteServiceIdentifier the remote service identifier
     * @throws IOException if an error occurs
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
     * @throws IOException if an error occurs
     */
    ContextIdentifier openContext(ServiceIdentifier serviceIdentifier) throws IOException;

    /**
     * Close a previously opened context.  The protocol handler should cause the
     * {@link org.jboss.cx.remoting.spi.protocol.ProtocolContext#closeContext(ContextIdentifier)} method to be called
     * on the remote side for this context identifier.
     *
     * @param contextIdentifier
     * @throws IOException
     */
    void closeContext(ContextIdentifier contextIdentifier) throws IOException;

    RequestIdentifier openRequest(ContextIdentifier contextIdentifier) throws IOException;

    /**
     * Get a new service identifier that will be used to request a service from the remote side.
     *
     * @return
     * @throws IOException
     */
    ServiceIdentifier openService() throws IOException;

    /**
     * Send a service activation request to the remote side.  The service identifier will have been obtained from
     * the {@link #openService()} method on this {@code ProtocolHandler}.
     *
     * @param serviceIdentifier the service identifier
     * @param locator the locator for the new service
     * @throws IOException if an error occurs
     */
    void sendServiceRequest(ServiceIdentifier serviceIdentifier, ServiceLocator<?, ?> locator) throws IOException;

    void closeService(ServiceIdentifier serviceIdentifier) throws IOException;

    void sendRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Request<?> request) throws IOException;

    void sendCancelRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) throws IOException;

    /* SESSION methods */

    /**
     * Open a stream on this session.  Since either side may open a stream, it is important that the client and
     * server side take precautions to ensure that both the client and server will not initiate the same stream at
     * the same time.
     *
     * @return the identifier of a stream
     * @throws IOException
     */
    StreamIdentifier openStream() throws IOException;

    /**
     * Close a stream on this session.  When a stream is closed from one side, the other side will send back
     * a close message as well.  Only when a close has been sent AND received for a stream, may the stream identifier
     * be released and possibly reused.
     *
     * @param streamIdentifier
     * @throws IOException
     */
    void closeStream(StreamIdentifier streamIdentifier) throws IOException;

    /**
     * Send data over a stream.
     *
     * @param streamIdentifier the stream to send data on
     * @param data the data to send
     * @throws IOException if an error occurs
     */
    void sendStreamData(StreamIdentifier streamIdentifier, Object data) throws IOException;

    /**
     * Close the session.
     *
     * @throws IOException
     */
    void closeSession() throws IOException;
}
