package org.jboss.cx.remoting;

import java.util.concurrent.ConcurrentMap;

/**
 *
 */
public interface ClientContext extends Closeable<ClientContext> {
    /**
     * Get the attributes for this end of the context as a map.
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
