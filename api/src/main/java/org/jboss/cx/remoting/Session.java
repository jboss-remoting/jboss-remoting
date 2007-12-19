package org.jboss.cx.remoting;

import java.util.concurrent.ConcurrentMap;

/**
 * Represents a point-to-point relationship with another endpoint.
 * <p/>
 * An open session may be associated with one or more connections, or it may be connectionless (e.g. UDP).
 * <p/>
 * A session may be shared safely among multiple threads.
 */
public interface Session {
    /**
     * Close this session.  Any associated connection(s) will be closed.  Calling this method multiple times has no
     * effect.
     */
    void close() throws RemotingException;

    /**
     * Get the session map.
     *
     * @return the session map
     */
    ConcurrentMap<Object, Object> getAttributes();

    /**
     * Get the name of the associated endpoint, if any.
     *
     * @return the name, or {@code null} if the remote endpoint is anonymous
     */
    String getEndpointName();

    /**
     * Establish an agreement to communicate with a service on the remote side.
     *
     * @param locator the locator for the service
     * @return a context source which may be used to create communication contexts
     */
    <I, O> ContextSource<I, O> openService(ServiceLocator<I, O> locator) throws RemotingException;
}
