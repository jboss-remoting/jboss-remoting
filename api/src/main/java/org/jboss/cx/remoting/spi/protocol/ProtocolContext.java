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

    void receiveReply(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Object reply);

    void receiveException(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception);

    void receiveCancelAcknowledge(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier);

    void receiveServiceClosing(ServiceIdentifier serviceIdentifier);

    void receiveContextClosing(ContextIdentifier contextIdentifier, boolean done);

    /* SERVER methods */

    void receiveServiceClose(ServiceIdentifier remoteServiceIdentifier);

    void receiveContextClose(ContextIdentifier remoteContextIdentifier, boolean immediate, boolean cancel, boolean interrupt);

    void receiveOpenedContext(ServiceIdentifier remoteServiceIdentifier, ContextIdentifier remoteContextIdentifier);

    void receiveRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Object request);

    void receiveCancelRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt);

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
