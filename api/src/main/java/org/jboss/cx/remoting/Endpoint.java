package org.jboss.cx.remoting;

import java.net.URI;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.spi.remote.RemoteClientEndpoint;
import org.jboss.cx.remoting.spi.remote.RemoteServiceEndpoint;

/**
 * A potential participant in a JBoss Remoting communications relationship.
 */
public interface Endpoint {
    /**
     * Get the endpoint attribute map.  This is a storage area for any data associated with this endpoint, including
     * (but not limited to) connection and protocol information, and application information.
     *
     * You must have the TODO permission to invoke this method.
     *
     * @return the endpoint map
     */
    ConcurrentMap<Object, Object> getAttributes();

    /**
     * Get the name of this endpoint.
     *
     * @return the endpoint name, or {@code null} if there is no name
     */
    String getName();

    /**
     * Create a client endpoint that can be used to receive incoming requests on this endpoint.  The client may be passed to a
     * remote endpoint as part of a request or a reply, or it may be used locally.
     *
     * You must have the TODO permission to invoke this method.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param requestListener the request listener
     * @return the client
     */
    <I, O> RemoteClientEndpoint<I, O> createClient(RequestListener<I, O> requestListener) throws RemotingException;

    /**
     * Create a client source that can be used to acquire clients associated with a request listener on this endpoint.
     * The client source may be passed to a remote endpoint as part of a request or a reply, or it may be used locally.
     * The objects that are produced by this method may be used to mass-produce {@code Client} instances.
     *
     * You must have the TODO permission to invoke this method.
     *
     * @param <I> the request type
     * @param <O> the reply type
     * @param requestListener the request listener
     * @return the context source
     */
    <I, O> RemoteServiceEndpoint<I, O> createService(RequestListener<I, O> requestListener) throws RemotingException;

    /**
     * Add a listener that is notified when a session is created.
     *
     * @param sessionListener the session listener
     */
    void addSessionListener(SessionListener sessionListener);

    /**
     * Remove a previously added session listener.
     *
     * @param sessionListener the session listener
     */
    void removeSessionListener(SessionListener sessionListener);
}
