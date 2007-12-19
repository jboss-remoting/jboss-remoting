package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.core.util.Logger;

/**
 *
 */
public final class CoreInboundService<I, O> {
    private static final Logger log = Logger.getLogger(CoreInboundService.class);

    private final CoreSession coreSession;
    private final ServiceIdentifier serviceIdentifier;
    private final RequestListener<I, O> requestListener;

    public CoreInboundService(final CoreEndpoint coreEndpoint, final CoreSession coreSession, final ServiceIdentifier serviceIdentifier, final ServiceLocator<I, O> locator) throws RemotingException {
        this.coreSession = coreSession;
        this.serviceIdentifier = serviceIdentifier;
        final CoreDeployedService<I, O> service = coreEndpoint.locateDeployedService(locator);
        requestListener = service.getRequestListener();
    }

    public void receivedOpenedContext(final ContextIdentifier remoteContextIdentifier) {
        coreSession.createServerContext(serviceIdentifier, remoteContextIdentifier, requestListener);
    }
}
