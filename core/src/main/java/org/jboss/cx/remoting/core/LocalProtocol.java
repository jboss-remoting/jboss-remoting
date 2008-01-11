package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.URI;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.core.util.Logger;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.MessageInput;
import org.jboss.cx.remoting.spi.protocol.MessageOutput;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.SimpleContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.SimpleRequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.SimpleServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.SimpleStreamIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public final class LocalProtocol {

    private static final Logger log = Logger.getLogger(LocalProtocol.class);

    private final ConcurrentMap<String, ProtocolServerContext> endpoints = CollectionUtil.concurrentMap();

    public void addToEndpoint(Endpoint endpoint) throws RemotingException {
        final ProtocolRegistration registration = endpoint.registerProtocol(ProtocolRegistrationSpec.DEFAULT.setScheme("local").setProtocolHandlerFactory(new Factory(endpoint)));
        final ProtocolServerContext serverContext = registration.getProtocolServerContext();
        if (endpoints.putIfAbsent(endpoint.getName(), serverContext) != null) {
            throw new IllegalArgumentException("Attempt to register duplicate endpoint \"" + endpoint.getName() + "\"");
        }
    }

    public final class Factory implements ProtocolHandlerFactory {
        private final String endpointName;

        private Factory(final Endpoint endpoint) {
            endpointName = endpoint.getName();
        }

        public boolean isLocal(URI uri) {
            return true;
        }

        public ProtocolHandler createHandler(ProtocolContext context, URI remoteUri, final CallbackHandler clientCallbackHandler, final CallbackHandler serverCallbackHandler) throws IOException {
            final String remoteName = remoteUri.getSchemeSpecificPart();
            final ProtocolServerContext serverContext = endpoints.get(remoteName);
            if (serverContext == null) {
                throw new IOException("No local endpoint named \"" + remoteName + "\" could be found");
            }
            return new Handler(serverContext.establishSession(new Handler(context)));
        }

        public void close() {
            endpoints.remove(endpointName);
        }
    }

    public final class Handler implements ProtocolHandler {
        private final ProtocolContext remoteContext;

        private Handler(final ProtocolContext remoteContext) {
            this.remoteContext = remoteContext;
        }

        public ContextIdentifier openContext(final ServiceIdentifier serviceIdentifier) throws IOException {
            log.trace("Opening context for local protocol");
            final SimpleContextIdentifier contextIdentifier = new SimpleContextIdentifier();
            remoteContext.receiveOpenedContext(serviceIdentifier, contextIdentifier);
            return contextIdentifier;
        }

        public RequestIdentifier openRequest(ContextIdentifier contextIdentifier) throws IOException {
            log.trace("Opening request for local protocol");
            return new SimpleRequestIdentifier();
        }

        public StreamIdentifier openStream() throws IOException {
            log.trace("Opening stream for local protocol");
            return new SimpleStreamIdentifier();
        }

        public ServiceIdentifier openService() throws IOException {
            return new SimpleServiceIdentifier();
        }

        public void closeSession() throws IOException {
            log.trace("Closing session for local protocol");
            remoteContext.closeSession();
        }

        public void closeService(ServiceIdentifier serviceIdentifier) throws IOException {
        }

        public void closeContext(ContextIdentifier contextIdentifier) throws IOException {
            log.trace("Closing context for local protocol");
            remoteContext.closeContext(contextIdentifier);
        }

        public void closeStream(StreamIdentifier streamIdentifier) throws IOException {
            log.trace("Closing stream for local protocol");
        }

        public StreamIdentifier readStreamIdentifier(ObjectInput input) throws IOException {
            throw new UnsupportedOperationException("streams");
        }

        public void writeStreamIdentifier(ObjectOutput output, StreamIdentifier identifier) throws IOException {
            throw new UnsupportedOperationException("streams");
        }

        public StreamIdentifier readStreamIdentifier(MessageInput input) throws IOException {
            throw new UnsupportedOperationException("streams");
        }

        public void writeStreamIdentifier(MessageOutput output, StreamIdentifier identifier) throws IOException {
            throw new UnsupportedOperationException("streams");
        }

        public void sendServiceRequest(ServiceIdentifier serviceIdentifier, ServiceLocator<?, ?> locator) throws IOException {
            log.trace("Sending service request for local protocol");
            remoteContext.receiveServiceRequest(serviceIdentifier, locator);
        }

        public void sendServiceActivate(ServiceIdentifier serviceIdentifier) throws IOException {
            log.trace("Sending service activation for local protocol");
            remoteContext.receiveServiceActivate(serviceIdentifier);
        }

        public void sendReply(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply) throws IOException {
            log.trace("Sending stream for local protocol");
            remoteContext.receiveReply(remoteContextIdentifier, requestIdentifier, reply);
        }

        public void sendException(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) throws IOException {
            log.trace("Sending exception for local protocol");
            remoteContext.receiveException(remoteContextIdentifier, requestIdentifier, exception);
        }

        public void sendRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Request<?> request, final Executor streamExecutor) throws IOException {
            log.trace("Sending request for local protocol");
            remoteContext.receiveRequest(contextIdentifier, requestIdentifier, request);
        }

        public void sendCancelAcknowledge(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier) throws IOException {
            log.trace("Sending cancel acknowledge for local protocol");
            remoteContext.receiveCancelAcknowledge(remoteContextIdentifier, requestIdentifier);
        }

        public void sendServiceTerminate(ServiceIdentifier remoteServiceIdentifier) throws IOException {
        }

        public void sendCancelRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) throws IOException {
            log.trace("Sending cancel request for local protocol");
            remoteContext.receiveCancelRequest(contextIdentifier, requestIdentifier, mayInterrupt);
        }

        public MessageOutput sendStreamData(StreamIdentifier streamIdentifier, final Executor streamExeceutor) throws IOException {
            throw new UnsupportedOperationException("streams");
        }
    }
}
