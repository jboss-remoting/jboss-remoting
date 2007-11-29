package org.jboss.cx.remoting;

import org.jboss.cx.remoting.spi.EndpointProvider;

/**
 *
 */
public final class Remoting {

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

    // privates

    private Remoting() { /* empty */ }
}
