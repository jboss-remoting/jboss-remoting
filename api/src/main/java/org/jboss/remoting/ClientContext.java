package org.jboss.remoting;

import java.util.concurrent.ConcurrentMap;

/**
 * The server context for a single remote client instance.
 *
 * @apiviz.exclude
 */
public interface ClientContext extends HandleableCloseable<ClientContext> {
    /**
     * Get the attributes for this end of the channel as a map.
     *
     * @return the attribute map
     */
    ConcurrentMap<Object, Object> getAttributes();

    /**
     * Get the service that this context is associated with, or {@code null} if there is no
     * service.
     *
     * @return the service, or {@code null} if there is none
     */
    ServiceContext getServiceContext();
}
