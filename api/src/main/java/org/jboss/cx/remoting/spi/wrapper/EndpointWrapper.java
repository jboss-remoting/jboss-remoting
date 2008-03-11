package org.jboss.cx.remoting.spi.wrapper;

import java.net.URI;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.ContextSource;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;

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

    public <I, O> Session openSession(final URI remoteUri, final AttributeMap attributeMap, final Context<I, O> rootContext) throws RemotingException {
        return delegate.openSession(remoteUri, attributeMap, rootContext);
    }

    public String getName() {
        return delegate.getName();
    }

    public ProtocolRegistration registerProtocol(final ProtocolRegistrationSpec spec) throws RemotingException, IllegalArgumentException {
        return delegate.registerProtocol(spec);
    }

    public <I, O> Context<I, O> createContext(final RequestListener<I, O> requestListener) {
        return delegate.createContext(requestListener);
    }

    public <I, O> ContextSource<I, O> createService(final RequestListener<I, O> requestListener) {
        return delegate.createService(requestListener);
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
