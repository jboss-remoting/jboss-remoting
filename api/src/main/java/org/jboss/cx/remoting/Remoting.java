package org.jboss.cx.remoting;

import org.jboss.cx.remoting.spi.EndpointProvider;
import org.jboss.cx.remoting.spi.wrapper.SessionWrapper;
import org.jboss.cx.remoting.spi.wrapper.ContextSourceWrapper;
import org.jboss.cx.remoting.core.util.Logger;
import java.net.URI;

/**
 *
 */
public final class Remoting {
    private static final Logger log = Logger.getLogger(Remoting.class);

    private static final class EndpointProviderHolder {
        private static final EndpointProvider provider;

        static {
            provider = load();
        }

        private static EndpointProvider load() {
            return load("org.jboss.cx.remoting.core.CoreEndpointProvider");
        }

        private static EndpointProvider load(String name) {
            try {
                return (EndpointProvider) Class.forName(name).newInstance();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Failed to instantiate Remoting endpoint provider: " + ex.getMessage(), ex);
            }
        }
    }

    public static Endpoint createEndpoint(String name) {
        return EndpointProviderHolder.provider.createEndpoint(name);
    }

    public static Session createEndpointAndSession(String endpointName, URI remoteUri, String userName, char[] password) throws RemotingException {
        final Endpoint endpoint = createEndpoint(endpointName);
        boolean ok = false;
        try {
            final Session session = new SessionWrapper(endpoint.openSession(EndpointLocator.DEFAULT.setEndpointUri(remoteUri).setClientAuthentication(userName, password))) {
                public void close() throws RemotingException {
                    try {
                        super.close();
                    } finally {
                        endpoint.shutdown();
                    }
                }
            };
            ok = true;
            return session;
        } finally {
            if (! ok) {
                endpoint.shutdown();
            }
        }
    }

    public static <I, O> ContextSource<I, O> createEndpointAndOpenService(String endpointName, URI remoteUri, String userName, char[] password, Class<I> requestType, Class<O> replyType, String serviceType, String serviceGroupName) throws RemotingException {
        final Session session = createEndpointAndSession(endpointName, remoteUri, userName, password);
        boolean ok = false;
        try {
            final ContextSource<I, O> service = new ContextSourceWrapper<I, O>(session.openService(ServiceLocator.DEFAULT.setRequestType(requestType).setReplyType(replyType).setServiceGroupName(serviceGroupName).setServiceType(serviceType))) {
                public void close() {
                    try {
                        super.close();
                    } finally {
                        try {
                            session.close();
                        } catch (RemotingException e) {
                            log.error(e, "Failed to close Remoting session");
                        }
                    }
                }
            };
            ok = true;
            return service;
        } finally {
            if (! ok) {
                session.close();
            }
        }
    }

    // privates

    private Remoting() { /* empty */ }
}
