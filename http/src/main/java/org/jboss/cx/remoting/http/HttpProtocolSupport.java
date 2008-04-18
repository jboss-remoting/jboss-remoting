package org.jboss.cx.remoting.http;

import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.spi.Registration;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.util.CollectionUtil;

/**
 *
 */
public final class HttpProtocolSupport {

    public HttpProtocolSupport() {/* empty */}

    // Accessors: dependency

    private Endpoint endpoint;
    private Random random;

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public Random getRandom() {
        return random;
    }

    public void setRandom(final Random random) {
        this.random = random;
    }

    // Accessors: configuration
    // (none)

    // Lifecycle

    private Registration registration;

    public void create() throws RemotingException {
        registration = endpoint.registerProtocol("http", new ProtocolHandlerFactory() {
            public boolean isLocal(final URI uri) {
                return false;
            }

            public ProtocolHandler createHandler(final ProtocolContext context, final URI remoteUri, final AttributeMap attributeMap) throws IOException {
                final RemotingHttpSession session = new RemotingHttpSession();
                final String sessionId;
                for (;;) {
                    final String generatedId = generateSessionId();
                    if (sessionMap.putIfAbsent(generatedId, session) == null) {
                        sessionId = generatedId;
                        break;
                    }
                }
                session.intialize(HttpProtocolSupport.this, sessionId, context);
                return session.getProtocolHandler();
            }

            public void close() {
            }
        });
        if (random == null) {
            random = new SecureRandom();
        }
    }

    public void start() {
        registration.start();
    }

    public void stop() {
        registration.stop();
    }

    public void destroy() {
        try {
            registration.unregister();
        } finally {
            endpoint = null;
            random = null;
            registration = null;
        }
    }

    // Session management

    private final ConcurrentMap<String, RemotingHttpSession> sessionMap = CollectionUtil.concurrentWeakValueMap();

    private String generateSessionId() {
        final byte[] bytes = new byte[32];
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        random.nextBytes(bytes);
        for (byte b : bytes) {
            builder.append(Character.digit(b >>> 4 & 15, 16));
            builder.append(Character.digit(b & 15, 16));
        }
        return builder.toString();
    }

    // todo - additional marshaller negotiation
    public void establishInboundSession() throws RemotingException {
        final RemotingHttpSession session = new RemotingHttpSession();
        final String sessionId;
        for (;;) {
            final String generatedId = generateSessionId();
            if (sessionMap.putIfAbsent(generatedId, session) == null) {
                sessionId = generatedId;
                break;
            }
        }
        final ProtocolContext protocolContext = endpoint.openIncomingSession(session.getProtocolHandler());
        session.intialize(this, sessionId, protocolContext);
    }

    RemotingHttpSession lookupSession(String sessionId) {
        return sessionMap.get(sessionId);
    }
}
