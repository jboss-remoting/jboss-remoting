package org.jboss.cx.remoting;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.core.CoreEndpoint;
import org.jboss.cx.remoting.core.protocol.LocalProtocolHandlerFactory;

/**
 *
 */
public final class Remoting {
    private static final Logger log = Logger.getLogger(Remoting.class);

    public static <I, O> Endpoint createEndpoint(String name, RequestListener<I, O> listener) throws RemotingException {
        final CoreEndpoint coreEndpoint = new CoreEndpoint(name, listener);
        final ExecutorService executorService = Executors.newCachedThreadPool();
        coreEndpoint.setExecutor(executorService);
        coreEndpoint.start();
        boolean ok = false;
        try {
            final Endpoint userEndpoint = coreEndpoint.getUserEndpoint();
            LocalProtocolHandlerFactory.addTo(userEndpoint);
            userEndpoint.addCloseHandler(new CloseHandler<Endpoint>() {
                public void handleClose(final Endpoint closed) {
                    executorService.shutdown();
                }
            });
            return userEndpoint;
        } finally {
            if (! ok) {
                coreEndpoint.stop();
            }
        }
    }

    public static Session createEndpointAndSession(String endpointName, URI remoteUri, final String userName, final char[] password) throws RemotingException {
        return null;
    }

    public static <I, O> ContextSource<I, O> createEndpointAndOpenService(String endpointName, URI remoteUri, String userName, char[] password, Class<I> requestType, Class<O> replyType, String serviceType, String serviceGroupName) throws RemotingException {
        return null;
    }

    // privates

    private Remoting() { /* empty */ }
}
