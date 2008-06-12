package org.jboss.cx.remoting.core.protocol;

import java.io.IOException;
import java.net.URI;
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
public final class LocalProtocolHandlerFactory implements ProtocolHandlerFactory {
    @SuppressWarnings ({"UnusedDeclaration"})
    private final Endpoint endpoint;
    private final String endpointName;

    public LocalProtocolHandlerFactory(final Endpoint endpoint) {
        this.endpoint = endpoint;
        endpointName = endpoint.getName();
    }

    private static final ConcurrentMap<String, Endpoint> endpoints = CollectionUtil.concurrentMap();

    public boolean isLocal(final URI uri) {
        return true;
    }

    public ProtocolHandler createHandler(final ProtocolContext ourProtocolContext, final URI remoteUri, final AttributeMap attributeMap) throws IOException {
        final String part = remoteUri.getSchemeSpecificPart();
        final int index = part.indexOf(':');
        final String otherEndpointName;
        if (index == -1) {
            otherEndpointName = part;
        } else {
            otherEndpointName = part.substring(0, index);
        }
        final Endpoint otherEndpoint = endpoints.get(otherEndpointName);
        if (otherEndpoint == null) {
            throw new RemotingException("No such local endpoint '" + otherEndpoint + "'");
        }
        final LocalProtocolHandler otherProtocolHandler = new LocalProtocolHandler(ourProtocolContext, otherEndpointName);
        final ProtocolContext otherProtocolContext = otherEndpoint.openSession(otherProtocolHandler, null);
        final LocalProtocolHandler ourProtocolHandler = new LocalProtocolHandler(otherProtocolContext, endpointName);
        otherProtocolContext.receiveRemoteSideReady(endpointName);
        ourProtocolContext.receiveRemoteSideReady(otherEndpointName);
        return ourProtocolHandler;
    }

    public void close() {
        
    }

    public static void addTo(final Endpoint endpoint) throws RemotingException {
        final String name = endpoint.getName();
        final LocalProtocolHandlerFactory handlerFactory = new LocalProtocolHandlerFactory(endpoint);
        final Registration registration = endpoint.registerProtocol("local", handlerFactory);
        registration.start();
        endpoints.putIfAbsent(name, endpoint);
    }
}
