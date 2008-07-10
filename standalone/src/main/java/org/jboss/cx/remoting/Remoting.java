package org.jboss.cx.remoting;

import java.io.IOException;
import org.jboss.cx.remoting.core.EndpointImpl;
import org.jboss.cx.remoting.spi.remote.RemoteClientEndpoint;
import org.jboss.cx.remoting.spi.remote.RemoteServiceEndpoint;

/**
 *
 */
public final class Remoting {
    // lifecycle lock
    private static final Object lifecycle = new Object();

    public static Endpoint createEndpoint(String name) throws IOException {
        synchronized (lifecycle) {
            final EndpointImpl endpointImpl = new EndpointImpl();
            endpointImpl.setName(name);
            endpointImpl.start();
            return endpointImpl;
        }
    }

    public static void closeEndpoint(Endpoint endpoint) {
        synchronized (lifecycle) {
            if (endpoint instanceof EndpointImpl) {
                final EndpointImpl endpointImpl = (EndpointImpl) endpoint;
                endpointImpl.stop();
            }
        }
    }

    public static <I, O> Client<I, O> createLocalClient(Endpoint endpoint, RequestListener<I, O> requestListener) throws RemotingException {
        final RemoteClientEndpoint<I, O> clientEndpoint = endpoint.createClientEndpoint(requestListener);
        try {
            return endpoint.createClient(clientEndpoint);
        } finally {
            clientEndpoint.autoClose();
        }
    }

    public static <I, O> ClientSource<I, O> createLocalClientSource(Endpoint endpoint, RequestListener<I, O> requestListener) throws RemotingException {
        final RemoteServiceEndpoint<I, O> serviceEndpoint = endpoint.createServiceEndpoint(requestListener);
        try {
            return endpoint.createClientSource(serviceEndpoint);
        } finally {
            serviceEndpoint.autoClose();
        }
    }

    // privates

    private Remoting() { /* empty */ }
}
