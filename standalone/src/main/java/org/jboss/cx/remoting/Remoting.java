package org.jboss.cx.remoting;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jboss.cx.remoting.core.CoreEndpoint;
import org.jboss.cx.remoting.core.protocol.LocalProtocolHandlerFactory;
import org.jboss.cx.remoting.jrpp.JrppProtocolSupport;
import org.jboss.cx.remoting.jrpp.JrppServer;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.util.AttributeMap;

/**
 *
 */
public final class Remoting {
    private static final Logger log = Logger.getLogger(Remoting.class);

    private static final String JRPP_SUPPORT_KEY = "org.jboss.cx.remoting.standalone.jrpp.support";

    public static <I, O> Endpoint createEndpoint(String name, RequestListener<I, O> listener) throws IOException {
        final CoreEndpoint coreEndpoint = new CoreEndpoint(name, listener);
        final ExecutorService executorService = Executors.newCachedThreadPool();
        coreEndpoint.setExecutor(executorService);
        coreEndpoint.start();
        boolean ok = false;
        try {
            final Endpoint userEndpoint = coreEndpoint.getUserEndpoint();
            LocalProtocolHandlerFactory.addTo(userEndpoint);
            final JrppProtocolSupport jrppProtocolSupport = new JrppProtocolSupport();
            jrppProtocolSupport.setEndpoint(userEndpoint);
            jrppProtocolSupport.setExecutor(executorService);
            jrppProtocolSupport.create();
            jrppProtocolSupport.start();
            userEndpoint.getAttributes().put(JRPP_SUPPORT_KEY, jrppProtocolSupport);
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

    public static JrppServer addJrppServer(Endpoint endpoint, SocketAddress address, AttributeMap attributeMap) throws IOException {
        final JrppServer jrppServer = new JrppServer();
        jrppServer.setProtocolSupport((JrppProtocolSupport) endpoint.getAttributes().get(JRPP_SUPPORT_KEY));
        jrppServer.setSocketAddress(new InetSocketAddress(12345));
        jrppServer.setAttributeMap(AttributeMap.EMPTY);
        jrppServer.setEndpoint(endpoint);
        jrppServer.create();
        jrppServer.start();
        endpoint.addCloseHandler(new CloseHandler<Endpoint>() {
            public void handleClose(final Endpoint closed) {
                try {
                    jrppServer.stop();
                } finally {
                    jrppServer.destroy();
                }
            }
        });
        return jrppServer;
    }

    // privates

    private Remoting() { /* empty */ }
}
