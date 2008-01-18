package org.jboss.cx.remoting.http;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.URI;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.Header;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.http.spi.RemotingHttpServerContext;
import org.jboss.cx.remoting.http.spi.RemotingHttpSessionContext;
import org.jboss.cx.remoting.http.spi.HttpTransporter;
import org.jboss.cx.remoting.http.spi.IncomingHttpMessage;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.spi.protocol.MessageOutput;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public final class HttpProtocolSupport {
    private final ProtocolHandlerFactory protocolHandlerFactory = new HttpProtocolHandlerFactory();
    private HttpTransporter httpTransporter;

    private final Endpoint endpoint;
    private final ProtocolRegistration registration;
    private final ProtocolServerContext serverContext;

    private final ConcurrentMap<String, RemotingHttpSessionContext> sessions = CollectionUtil.concurrentMap();

    public HttpProtocolSupport(final Endpoint endpoint) throws RemotingException {
        this.endpoint = endpoint;
        ProtocolRegistrationSpec spec = ProtocolRegistrationSpec.DEFAULT.setScheme("http").setProtocolHandlerFactory(protocolHandlerFactory);
        registration = endpoint.registerProtocol(spec);
        serverContext = registration.getProtocolServerContext();
    }

    public void setHttpTransporter(final HttpTransporter httpTransporter) {
        this.httpTransporter = httpTransporter;
    }

    public RemotingHttpServerContext addServer() {
        return new RemotingHttpServerContext() {
            public RemotingHttpSessionContext locateSession(IncomingHttpMessage message) {
                final String sessionId = message.getFirstHeaderValue("JBoss-Remoting-Session-ID");
                return sessionId == null ? null : sessions.get(sessionId);
            }
        };
    }

    public final class HttpProtocolHandlerFactory implements ProtocolHandlerFactory {

        public boolean isLocal(URI uri) {
            return false;
        }

        public ProtocolHandler createHandler(ProtocolContext context, URI remoteUri, CallbackHandler clientCallbackHandler) throws IOException {
            if (httpTransporter == null) {
                throw new IOException("No ability to initiate an HTTP connection (no transporter available)");
            }
            return new HttpServerContextImpl(httpTransporter).getProtocolHandler();
        }

        public void close() {
        }
    }

    public final class HttpServerContextImpl implements RemotingHttpServerContext {
        private final HttpTransporter transporter;
        private final ProtocolHandler protocolHandler = new ProtocolHandlerImpl();

        public HttpServerContextImpl(final HttpTransporter transporter) {
            this.transporter = transporter;
        }

        public RemotingHttpSessionContext locateSession(IncomingHttpMessage message) {
            return null;
        }

        public ProtocolHandler getProtocolHandler() {
            return protocolHandler;
        }

        public final class ProtocolHandlerImpl implements ProtocolHandler {

            public void sendServiceActivate(ServiceIdentifier remoteServiceIdentifier) throws IOException {
            }

            public void sendReply(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply) throws IOException {
                final Object body = reply.getBody();
                for (Header header : reply.getHeaders()) {
                }
            }

            public void sendException(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) throws IOException {
            }

            public void sendCancelAcknowledge(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier) throws IOException {
            }

            public void sendServiceTerminate(ServiceIdentifier remoteServiceIdentifier) throws IOException {
            }

            public ContextIdentifier openContext(ServiceIdentifier serviceIdentifier) throws IOException {
                return null;
            }

            public void closeContext(ContextIdentifier contextIdentifier) throws IOException {
            }

            public RequestIdentifier openRequest(ContextIdentifier contextIdentifier) throws IOException {
                return null;
            }

            public ServiceIdentifier openService() throws IOException {
                return null;
            }

            public void sendServiceRequest(ServiceIdentifier serviceIdentifier, ServiceLocator<?, ?> locator) throws IOException {
            }

            public void closeService(ServiceIdentifier serviceIdentifier) throws IOException {
            }

            public void sendRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Request<?> request, Executor streamExecutor) throws IOException {
            }

            public void sendCancelRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) throws IOException {
            }

            public StreamIdentifier openStream() throws IOException {
                return null;
            }

            public void closeStream(StreamIdentifier streamIdentifier) throws IOException {
            }

            public StreamIdentifier readStreamIdentifier(ObjectInput input) throws IOException {
                return null;
            }

            public void writeStreamIdentifier(ObjectOutput output, StreamIdentifier identifier) throws IOException {
            }

            public MessageOutput sendStreamData(StreamIdentifier streamIdentifier, Executor streamExecutor) throws IOException {
                return null;
            }

            public void closeSession() throws IOException {
            }

            public String getRemoteEndpointName() {
                return null;
            }
        }
    }
}
