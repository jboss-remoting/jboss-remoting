package org.jboss.cx.remoting.spi.protocol;

import java.io.IOException;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.spi.ByteMessageInput;
import org.jboss.cx.remoting.spi.ByteMessageOutput;
import org.jboss.cx.remoting.spi.ObjectMessageInput;
import org.jboss.cx.remoting.spi.ObjectMessageOutput;

/**
 *
 */
public interface ProtocolContext {

    /* CLIENT methods */

    void receiveReply(ClientIdentifier clientIdentifier, RequestIdentifier requestIdentifier, Object reply);

    void receiveException(ClientIdentifier clientIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception);

    void receiveCancelAcknowledge(ClientIdentifier clientIdentifier, RequestIdentifier requestIdentifier);

    void receiveServiceClosing(ServiceIdentifier serviceIdentifier);

    void receiveClientClosing(ClientIdentifier clientIdentifier, boolean done);

    /* SERVER methods */

    void receiveServiceClose(ServiceIdentifier remoteServiceIdentifier);

    void receiveClientClose(ClientIdentifier remoteClientIdentifier, boolean immediate, boolean cancel, boolean interrupt);

    void receiveOpenedContext(ServiceIdentifier remoteServiceIdentifier, ClientIdentifier remoteClientIdentifier);

    void receiveRequest(ClientIdentifier remoteClientIdentifier, RequestIdentifier requestIdentifier, Object request);

    void receiveCancelRequest(ClientIdentifier remoteClientIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt);

    /* SESSION methods */

    void closeStream(StreamIdentifier streamIdentifier);

    void receiveStreamData(StreamIdentifier streamIdentifier, ObjectMessageInput data);

    void openSession(String remoteEndpointName);

    void closeSession();

    /* CLIENT OR SERVER methods */

    ObjectMessageOutput getMessageOutput(ByteMessageOutput target) throws IOException;

    ObjectMessageOutput getMessageOutput(ByteMessageOutput target, Executor streamExecutor) throws IOException;

    ObjectMessageInput getMessageInput(ByteMessageInput source) throws IOException;

    String getLocalEndpointName();
}
