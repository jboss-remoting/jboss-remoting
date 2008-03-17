package org.jboss.cx.remoting.http;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentMap;
import java.util.Random;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.http.spi.RemotingHttpServerContext;
import org.jboss.cx.remoting.http.spi.RemotingHttpSessionContext;
import org.jboss.cx.remoting.http.spi.HttpTransporter;
import org.jboss.cx.remoting.http.spi.IncomingHttpMessage;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;

/**
 *
 */
public final class HttpProtocolSupport {
    private final ProtocolHandlerFactory protocolHandlerFactory = new HttpProtocolHandlerFactory();
    private HttpTransporter httpTransporter;

    private final Endpoint endpoint;
    // todo - need secure random?
    private final Random random = new Random();

    private final ConcurrentMap<String, RemotingHttpSessionContext> sessions = CollectionUtil.concurrentMap();

    public HttpProtocolSupport(final Endpoint endpoint) throws RemotingException {
        this.endpoint = endpoint;
        endpoint.registerProtocol("http", protocolHandlerFactory);
    }

    public void setHttpTransporter(final HttpTransporter httpTransporter) {
        this.httpTransporter = httpTransporter;
    }

    public RemotingHttpServerContext addServer() {
        return new RemotingHttpServerContext() {
            public RemotingHttpSessionContext locateSession(IncomingHttpMessage message) {
                final String sessionId = message.getFirstHeaderValue(Http.HEADER_SESSION_ID);
                return sessionId == null ? null : sessions.get(sessionId);
            }
        };
    }

    public String generateSessionId() {
        return Long.toString(random.nextLong());
    }

    public boolean registerSession(String idStr, RemotingHttpSessionContext context) {
        return sessions.putIfAbsent(idStr, context) == null;
    }

    public final class HttpProtocolHandlerFactory implements ProtocolHandlerFactory {
        public boolean isLocal(URI uri) {
            return false;
        }

        public ProtocolHandler createHandler(ProtocolContext context, URI remoteUri, final AttributeMap attributeMap) throws IOException {
            if (httpTransporter == null) {
                throw new IOException("No ability to initiate an HTTP connection (no transporter available)");
            }
            return new RemotingHttpSessionImpl(HttpProtocolSupport.this, context).getProtocolHandler();
        }

        public void close() {
        }
    }
}
