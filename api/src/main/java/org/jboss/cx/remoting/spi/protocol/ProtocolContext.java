package org.jboss.cx.remoting.spi.protocol;

import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.ServiceLocator;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;

/**
 *
 */
public interface ProtocolContext {

    /* CLIENT methods */

    void receiveServiceActivate(ServiceIdentifier serviceIdentifier);

    void receiveReply(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply);

    void receiveException(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception);

    void receiveCancelAcknowledge(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier);

    void receiveServiceTerminate(ServiceIdentifier serviceIdentifier);

    <T> Request<T> createRequest(T body);

    /* SERVER methods */

    void closeContext(ContextIdentifier remoteContextIdentifier);

    void receiveServiceRequest(ServiceIdentifier remoteServiceIdentifier, ServiceLocator<?, ?> locator);

    void closeService(ServiceIdentifier remoteServiceIdentifier);

    void receiveOpenedContext(ServiceIdentifier remoteServiceIdentifier, ContextIdentifier remoteContextIdentifier);

    void receiveRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Request<?> request);

    void receiveCancelRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt);

    <T> Reply<T> createReply(T body);

    /* SESSION methods */

    void closeStream(StreamIdentifier streamIdentifier);

    void receiveStreamData(StreamIdentifier streamIdentifier, Object data);

    void closeSession();

    /* CLIENT OR SERVER methods */

    void serializeTo(Object src, OutputStream target) throws IOException;

    Object deserializeFrom(InputStream source) throws IOException;
}
