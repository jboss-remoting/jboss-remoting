package org.jboss.remoting;

import java.io.IOException;
import org.jboss.remoting.core.EndpointImpl;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.remoting.spi.Handle;
import org.jboss.xnio.IoUtils;

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

    public static <I, O> Client<I, O> createLocalClient(final Endpoint endpoint, final RequestListener<I, O> requestListener, final Class<I> requestClass, final Class<O> replyClass) throws IOException {
        final Handle<RequestHandler> handle = endpoint.createRequestHandler(requestListener, requestClass, replyClass);
        try {
            return endpoint.createClient(handle.getResource(), requestClass, replyClass);
        } finally {
            IoUtils.safeClose(handle);
        }
    }

    public static <I, O> ClientSource<I, O> createLocalClientSource(final Endpoint endpoint, final LocalServiceConfiguration<I, O> config) throws IOException {
        final Handle<RequestHandlerSource> handle = endpoint.registerService(config);
        try {
            return endpoint.createClientSource(handle.getResource(), config.getRequestClass(), config.getReplyClass());
        } finally {
            IoUtils.safeClose(handle);
        }
    }

    // privates

    private Remoting() { /* empty */ }
}
