package org.jboss.cx.remoting;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentMap;
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

    // lifecycle lock
    private static final Object lifecycle = new Object();

    public static <I, O> Endpoint createEndpoint(String name, RequestListener<I, O> listener) throws IOException {
        synchronized (lifecycle) {
            boolean ok = false;
            final CoreEndpoint coreEndpoint = new CoreEndpoint();
            coreEndpoint.setName(name);
            coreEndpoint.setRootListener(listener);
            coreEndpoint.create();
            try {
                coreEndpoint.start();
                try {
                    LocalProtocolHandlerFactory.addTo(coreEndpoint);
                    final JrppProtocolSupport jrppProtocolSupport = new JrppProtocolSupport();
                    jrppProtocolSupport.setEndpoint(coreEndpoint);
                    jrppProtocolSupport.create();
                    try {
                        jrppProtocolSupport.start();
                        try {
                            final ConcurrentMap<Object, Object> attributes = coreEndpoint.getAttributes();
                            attributes.put(JRPP_SUPPORT_KEY, jrppProtocolSupport);
                            ok = true;
                            return coreEndpoint;
                        } finally {
                            if (! ok) {
                                jrppProtocolSupport.stop();
                            }
                        }
                    } finally {
                        if (! ok) {
                            jrppProtocolSupport.destroy();
                        }
                    }
                } finally {
                    if (! ok) {
                        coreEndpoint.stop();
                    }
                }
            } finally {
                if (! ok) {
                    coreEndpoint.destroy();
                }
            }
        }
    }

    public static void closeEndpoint(Endpoint endpoint) {
        synchronized (lifecycle) {
            if (endpoint instanceof CoreEndpoint) {
                final CoreEndpoint coreEndpoint = (CoreEndpoint) endpoint;
                final ConcurrentMap<Object, Object> attributes = coreEndpoint.getAttributes();
                final JrppProtocolSupport jrppProtocolSupport = (JrppProtocolSupport) attributes.remove(JRPP_SUPPORT_KEY);
                coreEndpoint.stop();
                coreEndpoint.destroy();
                if (jrppProtocolSupport != null) {
                    jrppProtocolSupport.stop();
                    jrppProtocolSupport.destroy();
                }
            }
        }
    }

    public static JrppServer addJrppServer(Endpoint endpoint, SocketAddress address, AttributeMap attributeMap) throws IOException {
        synchronized (lifecycle) {
            boolean ok = false;
            final JrppServer jrppServer = new JrppServer();
            jrppServer.setProtocolSupport((JrppProtocolSupport) endpoint.getAttributes().get(JRPP_SUPPORT_KEY));
            jrppServer.setSocketAddress(address);
            jrppServer.setAttributeMap(attributeMap);
            jrppServer.setEndpoint(endpoint);
            jrppServer.create();
            try {
                jrppServer.start();
                ok = true;
                return jrppServer;
            } finally {
                if (! ok) {
                    jrppServer.destroy();
                }
            }
        }
    }

    // privates

    private Remoting() { /* empty */ }
}
