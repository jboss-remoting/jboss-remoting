package org.jboss.cx.remoting.spi.protocol;

import java.io.IOException;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.core.util.ByteInput;
import org.jboss.cx.remoting.core.util.ByteOutput;
import org.jboss.cx.remoting.core.util.MessageOutput;
import org.jboss.cx.remoting.core.util.MessageInput;

/**
 *
 */
public interface ProtocolContext {

    /* CLIENT methods */

    void receiveServiceActivate(ServiceIdentifier serviceIdentifier);

    void receiveReply(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Object reply);

    void receiveException(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception);

    void receiveCancelAcknowledge(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier);

    void receiveServiceTerminate(ServiceIdentifier serviceIdentifier);

    /* SERVER methods */

    void closeContext(ContextIdentifier remoteContextIdentifier);

    void receiveServiceRequest(ServiceIdentifier remoteServiceIdentifier, ServiceLocator<?, ?> locator);

    void closeService(ServiceIdentifier remoteServiceIdentifier);

    void receiveOpenedContext(ServiceIdentifier remoteServiceIdentifier, ContextIdentifier remoteContextIdentifier);

    void receiveRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Object request);

    void receiveCancelRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt);

    /* SESSION methods */

    void closeStream(StreamIdentifier streamIdentifier);

    void receiveStreamData(StreamIdentifier streamIdentifier, MessageInput data);

    void openSession(String remoteEndpointName);

    void closeSession();

    /* CLIENT OR SERVER methods */

    MessageOutput getMessageOutput(ByteOutput target) throws IOException;

    MessageOutput getMessageOutput(ByteOutput target, Executor streamExecutor) throws IOException;

    MessageInput getMessageInput(ByteInput source) throws IOException;

    String getLocalEndpointName();
}
