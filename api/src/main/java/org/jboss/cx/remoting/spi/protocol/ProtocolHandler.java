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
    ContextIdentifier openContext(ServiceIdentifier serviceIdentifier) throws IOException;

    RequestIdentifier openRequest(ContextIdentifier contextIdentifier) throws IOException;

    StreamIdentifier openStream(ContextIdentifier contextIdentifier) throws IOException;

    ServiceIdentifier openService() throws IOException;

    void closeSession() throws IOException;

    void closeService(ServiceIdentifier serviceIdentifier) throws IOException;

    void closeContext(ContextIdentifier contextIdentifier) throws IOException;

    void closeRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier) throws IOException;

    void closeStream(ContextIdentifier contextIdentifier, StreamIdentifier streamIdentifier) throws IOException;

    void sendServiceRequest(ServiceIdentifier serviceIdentifier, ServiceLocator<?, ?> locator) throws IOException;

    void sendServiceActivate(ServiceIdentifier serviceIdentifier) throws IOException;

    void sendReply(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply) throws IOException;

    void sendException(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) throws IOException;

    void sendRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Request<?> request) throws IOException;

    void sendCancelAcknowledge(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier) throws IOException;

    void sendCancelRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) throws IOException;

    void sendStreamData(ContextIdentifier contextIdentifier, StreamIdentifier streamIdentifier, Object data) throws IOException;
}
