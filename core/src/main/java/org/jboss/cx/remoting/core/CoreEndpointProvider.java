package org.jboss.cx.remoting.core;

import java.util.HashSet;
import java.util.Set;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.spi.EndpointProvider;

/**
 *
 */
public final class CoreEndpointProvider implements EndpointProvider {
    private final LocalProtocol localProtocol = new LocalProtocol();
    private final Set<String> endpointNames = CollectionUtil.synchronizedSet(new HashSet<String>());

    public Endpoint createEndpoint(String name) {
        // todo - need a way to signal the removal of an endpoint
        if (! endpointNames.add(name)) {
            throw new IllegalArgumentException("Failed to create endpoint (endpoint with the same name already exists");
        }
        final Endpoint userEndpoint = new CoreEndpoint(name).getUserEndpoint();
        try {
            localProtocol.addToEndpoint(userEndpoint);
        } catch (RemotingException e) {
            throw new IllegalStateException("Cannot create endpoint", e);
        }
        return userEndpoint;
    }
}
