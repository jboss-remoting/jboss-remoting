package org.jboss.cx.remoting.core.protocol;

import java.io.IOException;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.util.ObjectMessageOutput;
import org.jboss.cx.remoting.spi.protocol.ClientIdentifier;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.SimpleClientIdentifier;
import org.jboss.cx.remoting.spi.protocol.SimpleRequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;

/**
 *
 */
public final class LocalProtocolHandler implements ProtocolHandler {
    private final ProtocolContext target;
    private String remoteEndpointName;
    private static final ClientIdentifier ROOT_IDENTIFIER = new SimpleClientIdentifier();

    public LocalProtocolHandler(final ProtocolContext target, final String remoteEndpointName) {
        this.target = target;
        this.remoteEndpointName = remoteEndpointName;
    }

    public void sendReply(final ClientIdentifier remoteClientIdentifier, final RequestIdentifier requestIdentifier, final Object reply) throws IOException {
        target.receiveReply(remoteClientIdentifier, requestIdentifier, reply);
    }

    public void sendException(final ClientIdentifier remoteClientIdentifier, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) throws IOException {
        target.receiveException(remoteClientIdentifier, requestIdentifier, exception);
    }

    public void sendCancelAcknowledge(final ClientIdentifier remoteClientIdentifier, final RequestIdentifier requestIdentifier) throws IOException {
        target.receiveCancelAcknowledge(remoteClientIdentifier, requestIdentifier);
    }

    public void sendServiceClosing(final ServiceIdentifier remoteServiceIdentifier) throws IOException {
        target.receiveServiceClosing(remoteServiceIdentifier);
    }

    public void sendClientClosing(final ClientIdentifier remoteClientIdentifier, final boolean done) throws IOException {
        target.receiveClientClosing(remoteClientIdentifier, done);
    }

    public ClientIdentifier getLocalRootClientIdentifier() {
        return ROOT_IDENTIFIER;
    }

    public ClientIdentifier getRemoteRootClientIdentifier() {
        return ROOT_IDENTIFIER;
    }

    public ClientIdentifier openClient(final ServiceIdentifier serviceIdentifier) throws IOException {
        return null;
    }

    public void sendClientClose(final ClientIdentifier clientIdentifier, final boolean immediate, final boolean cancel, final boolean interrupt) throws IOException {
        target.receiveClientClose(clientIdentifier, immediate, cancel, interrupt);
    }

    public RequestIdentifier openRequest(final ClientIdentifier clientIdentifier) throws IOException {
        return new SimpleRequestIdentifier();
    }

    public void sendServiceClose(final ServiceIdentifier serviceIdentifier) throws IOException {
        target.receiveServiceClose(serviceIdentifier);
    }

    public void sendRequest(final ClientIdentifier clientIdentifier, final RequestIdentifier requestIdentifier, final Object request, final Executor streamExecutor) throws IOException {
        target.receiveRequest(clientIdentifier, requestIdentifier, request);
    }

    public void sendCancelRequest(final ClientIdentifier clientIdentifier, final RequestIdentifier requestIdentifier, final boolean mayInterrupt) throws IOException {
        target.receiveCancelRequest(clientIdentifier, requestIdentifier, mayInterrupt);
    }

    public ClientIdentifier openClient() throws IOException {
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
