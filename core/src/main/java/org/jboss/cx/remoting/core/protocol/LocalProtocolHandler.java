package org.jboss.cx.remoting.core.protocol;

import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.spi.ObjectMessageOutput;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.RemoteExecutionException;
import java.net.URI;
import java.io.IOException;
import java.util.concurrent.Executor;

/**
 *
 */
public final class LocalProtocolHandler implements ProtocolHandler {
    public LocalProtocolHandler(final ProtocolContext context, final URI remoteUri, final AttributeMap attributeMap) {

    }

    public void sendReply(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier, final Object reply) throws IOException {
    }

    public void sendException(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) throws IOException {
    }

    public void sendCancelAcknowledge(final ContextIdentifier remoteContextIdentifier, final RequestIdentifier requestIdentifier) throws IOException {
    }

    public void sendServiceClosing(final ServiceIdentifier remoteServiceIdentifier) throws IOException {
    }

    public void sendContextClosing(final ContextIdentifier remoteContextIdentifier, final boolean done) throws IOException {
    }

    public ContextIdentifier getLocalRootContextIdentifier() {
        return null;
    }

    public ContextIdentifier getRemoteRootContextIdentifier() {
        return null;
    }

    public ContextIdentifier openContext(final ServiceIdentifier serviceIdentifier) throws IOException {
        return null;
    }

    public void sendContextClose(final ContextIdentifier contextIdentifier, final boolean immediate, final boolean cancel, final boolean interrupt) throws IOException {
    }

    public RequestIdentifier openRequest(final ContextIdentifier contextIdentifier) throws IOException {
        return null;
    }

    public void sendServiceClose(final ServiceIdentifier serviceIdentifier) throws IOException {
    }

    public void sendRequest(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final Object request, final Executor streamExecutor) throws IOException {
    }

    public void sendCancelRequest(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final boolean mayInterrupt) throws IOException {
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
    }

    public ObjectMessageOutput sendStreamData(final StreamIdentifier streamIdentifier, final Executor streamExecutor) throws IOException {
        return null;
    }

    public void closeSession() throws IOException {
    }

    public String getRemoteEndpointName() {
        return null;
    }
}
