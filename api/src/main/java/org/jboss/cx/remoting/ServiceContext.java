package org.jboss.cx.remoting;

import java.util.concurrent.ConcurrentMap;

/**
 * The server-side context of a service.  Used to hold state relating to a service (known as a {@code ContextSource} on
 * the client side).
 */
public interface ServiceContext extends HandleableCloseable<ServiceContext> {

    /**
     * Get an attribute map which can be used to cache arbitrary state on the server side.
     *
     * @return the attribute map
     */
    ConcurrentMap<Object, Object> getAttributes();
}
