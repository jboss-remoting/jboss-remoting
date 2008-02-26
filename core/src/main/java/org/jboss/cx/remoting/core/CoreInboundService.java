package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;

/**
 *
 */
public final class CoreInboundService<I, O> {
    private final CoreSession coreSession;
    private final ServiceIdentifier serviceIdentifier;
    private final RequestListener<I, O> requestListener;

    public CoreInboundService(final CoreSession coreSession, final ServiceIdentifier serviceIdentifier, final RequestListener<I, O> requestListener) throws RemotingException {
        this.coreSession = coreSession;
        this.serviceIdentifier = serviceIdentifier;
        this.requestListener = requestListener;
    }

    void receivedOpenedContext(final ContextIdentifier remoteContextIdentifier) {
        coreSession.createServerContext(serviceIdentifier, remoteContextIdentifier, requestListener);
    }
}
