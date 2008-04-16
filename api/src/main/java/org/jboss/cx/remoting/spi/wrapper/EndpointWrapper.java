package org.jboss.cx.remoting.spi.wrapper;

import java.net.URI;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.ClientSource;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.SessionListener;
import org.jboss.cx.remoting.spi.Registration;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.util.AttributeMap;

/**
 *
 */
public class EndpointWrapper implements Endpoint {
    protected final Endpoint delegate;

    protected EndpointWrapper(final Endpoint endpoint) {
        delegate = endpoint;
    }

    public ConcurrentMap<Object, Object> getAttributes() {
        return delegate.getAttributes();
    }

    public Session openSession(final URI remoteUri, final AttributeMap attributeMap) throws RemotingException {
        return delegate.openSession(remoteUri, attributeMap);
    }

    public ProtocolContext openIncomingSession(final ProtocolHandler handler) throws RemotingException {
        return delegate.openIncomingSession(handler);
    }

    public String getName() {
        return delegate.getName();
    }

    public Registration registerProtocol(String scheme, ProtocolHandlerFactory protocolHandlerFactory) throws RemotingException, IllegalArgumentException {
        return delegate.registerProtocol(scheme, protocolHandlerFactory);
    }

    public <I, O> Client<I, O> createContext(final RequestListener<I, O> requestListener) {
        return delegate.createContext(requestListener);
    }

    public <I, O> ClientSource<I, O> createService(final RequestListener<I, O> requestListener) {
        return delegate.createService(requestListener);
    }

    public void addSessionListener(final SessionListener sessionListener) {
        delegate.addSessionListener(sessionListener);
    }

    public void removeSessionListener(final SessionListener sessionListener) {
        delegate.removeSessionListener(sessionListener);
    }

    public void close() throws RemotingException {
        delegate.close();
    }

    public void closeImmediate() throws RemotingException {
        delegate.closeImmediate();
    }

    public void addCloseHandler(final CloseHandler<Endpoint> closeHandler) {
        delegate.addCloseHandler(new CloseHandler<Endpoint>() {
            public void handleClose(final Endpoint closed) {
                closeHandler.handleClose(EndpointWrapper.this);
            }
        });
    }
}
