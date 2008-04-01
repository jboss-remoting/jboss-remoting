package org.jboss.cx.remoting.core.protocol;

import java.io.IOException;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.spi.ObjectMessageOutput;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.SimpleContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.SimpleRequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;

/**
 *
 */
public final class LocalProtocolHandler implements ProtocolHandler {
    private final ProtocolContext target;
    private String remoteEndpointName;
    private static final ContextIdentifier ROOT_IDENTIFIER = new SimpleContextIdentifier();

    public LocalProtocolHandler(final ProtocolContext target, final String remoteEndpointName) {
        this.target = target;
        this.remoteEndpointName = remoteEndpointName;
    }

    public void sendReply(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier, final Object reply) throws IOException {
        target.receiveReply(remoteContextIdentifier, requestIdentifier, reply);
    }

    public void sendException(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) throws IOException {
        target.receiveException(remoteContextIdentifier, requestIdentifier, exception);
    }

    public void sendCancelAcknowledge(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier) throws IOException {
        target.receiveCancelAcknowledge(remoteContextIdentifier, requestIdentifier);
    }

    public void sendServiceClosing(final ServiceIdentifier remoteServiceIdentifier) throws IOException {
        target.receiveServiceClosing(remoteServiceIdentifier);
    }

    public void sendContextClosing(final ContextIdentifier remoteContextIdentifier, final boolean done) throws IOException {
        target.receiveContextClosing(remoteContextIdentifier, done);
    }

    public ContextIdentifier getLocalRootContextIdentifier() {
        return ROOT_IDENTIFIER;
    }

    public ContextIdentifier getRemoteRootContextIdentifier() {
        return ROOT_IDENTIFIER;
    }

    public ContextIdentifier openContext(final ServiceIdentifier serviceIdentifier) throws IOException {
        return null;
    }

    public void sendContextClose(final ContextIdentifier contextIdentifier, final boolean immediate, final boolean cancel, final boolean interrupt) throws IOException {
        target.receiveContextClose(contextIdentifier, immediate, cancel, interrupt);
    }

    public RequestIdentifier openRequest(final ContextIdentifier contextIdentifier) throws IOException {
        return new SimpleRequestIdentifier();
    }

    public void sendServiceClose(final ServiceIdentifier serviceIdentifier) throws IOException {
        target.receiveServiceClose(serviceIdentifier);
    }

    public void sendRequest(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final Object request, final Executor streamExecutor) throws IOException {
        target.receiveRequest(contextIdentifier, requestIdentifier, request);
    }

    public void sendCancelRequest(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final boolean mayInterrupt) throws IOException {
        target.receiveCancelRequest(contextIdentifier, requestIdentifier, mayInterrupt);
    }

    public ContextIdentifier openContext() throws IOException {
        return null;
    }

    public ServiceIdentifier openService() throws IOException {
        return null;
    }

    public StreamIdentifier openStream() throws IOException {
        return null;
    }

    public void closeStream(final StreamIdentifier streamIdentifier) throws IOException {
        // N/A
    }

    public ObjectMessageOutput sendStreamData(final StreamIdentifier streamIdentifier, final Executor streamExecutor) throws IOException {
        // N/A
        return null;
    }

    public void closeSession() throws IOException {
        target.closeSession();
    }

    public String getRemoteEndpointName() {
        return remoteEndpointName;
    }
}
