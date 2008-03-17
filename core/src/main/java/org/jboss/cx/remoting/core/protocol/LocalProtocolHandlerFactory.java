package org.jboss.cx.remoting.core.protocol;

import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.Registration;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import java.net.URI;
import java.io.IOException;
import java.util.concurrent.ConcurrentMap;

/**
 *
 */
public final class LocalProtocolHandlerFactory implements ProtocolHandlerFactory {
    @SuppressWarnings ({"UnusedDeclaration"})
    private final Endpoint endpoint;

    public LocalProtocolHandlerFactory(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    private static final ConcurrentMap<String, LocalProtocolHandlerFactory> endpoints = CollectionUtil.concurrentMap();

    public boolean isLocal(final URI uri) {
        return true;
    }

    public ProtocolHandler createHandler(final ProtocolContext context, final URI remoteUri, final AttributeMap attributeMap) throws IOException {

        return new LocalProtocolHandler(context, remoteUri, attributeMap);
    }

    public void close() {
        
    }

    public static void addTo(final Endpoint endpoint) throws RemotingException {
        final String name = endpoint.getName();
        final LocalProtocolHandlerFactory handlerFactory = new LocalProtocolHandlerFactory(endpoint);
        final Registration registration = endpoint.registerProtocol("local", handlerFactory);
        registration.start();
        endpoints.putIfAbsent(name, handlerFactory);
    }
}
