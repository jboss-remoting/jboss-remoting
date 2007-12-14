package org.jboss.cx.remoting;

import java.net.URI;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.spi.Discovery;
import org.jboss.cx.remoting.spi.Registration;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;

import javax.security.auth.callback.CallbackHandler;

/**
 * A potential participant in a JBoss Remoting communications relationship.
 */
public interface Endpoint {
    /**
     * Get the endpoint attribute map.  This is a storage area for any data associated with this endpoint, including
     * (but not limited to) connection and protocol information, and application information.
     *
     * @return the endpoint map
     */
    ConcurrentMap<Object, Object> getAttributes();

    /**
     * Shut down this endpoint.  Cancel any outstanding requests, tear down thread pools.
     */
    void shutdown();

    /**
     * Open a session with another endpoint.  The protocol used is determined by the URI scheme.  The URI user-info part
     * must be {@code null} unless the specific protocol has an additional authentication scheme (e.g. HTTP BASIC).  The
     * authority is used to locate the server (the exact interpretation is dependent upon the protocol). The URI path is
     * the service to connect to.  The path may be relative to a protocol-specific deployment path.
     *
     * @param endpointLocator the endpoint locator
     * @return a new session
     *
     * @throws RemotingException if there is a problem creating the session, or if the request or reply type does not
     * match the remote service
     */
    Session openSession(EndpointLocator endpointLocator) throws RemotingException;

    /**
     * Get the name of this endpoint.
     *
     * @return the endpoint name, or {@code null} if there is no name
     */
    String getName();

    /**
     * Deploy a service into this endpoint.
     *
     * @param spec the specification for this service deployment
     *
     * @return a registration that may be used to control this deployment
     *
     * @throws RemotingException if the registration failed
     * @throws IllegalArgumentException if the specification failed validation
     */
    Registration deployService(ServiceDeploymentSpec spec) throws RemotingException, IllegalArgumentException;

    /**
     * Register a protocol specification for this endpoint.
     *
     * @param spec the protocol specification
     *
     * @return a registration that may be used to control this deployment
     *
     * @throws RemotingException if the protocol registration failed
     * @throws IllegalArgumentException if the specification failed validation
     */
    ProtocolRegistration registerProtocol(ProtocolRegistrationSpec spec) throws RemotingException, IllegalArgumentException;

    /**
     * Deploy a context interceptor type into this endpoint.  Subsequent sessions may negotiate to use this context
     * service.
     *
     * @param spec the deployment specification
     *
     * @return a registration that may be used to control this deployment
     *
     * @throws RemotingException if the registration failed
     * @throws IllegalArgumentException if the specification failed validation
     */
    Registration deployInterceptorType(InterceptorDeploymentSpec spec) throws RemotingException, IllegalArgumentException;

    /**
     * Discover a remote endpoint.  Adds the host to the internal routing table of the endpoint.  Higher cost indicates
     * a less desirable route.
     * <p/>
     * The next hop URI should also include a path component, if the target endpoint is deployed relative to a base path
     * (e.g. a servlet).
     *
     * @param endpointName the name of the discovered endpoint
     * @param nextHop the URI of the means to connect to the next "hop" towards the named endpoint
     * @param cost the "cost" associated with traversing this route
     *
     * @return an obejct representing the discovery
     *
     * @throws RemotingException if there is a problem with the discovery parameters
     */
    Discovery discover(String endpointName, URI nextHop, int cost) throws RemotingException;

    /**
     * Get the callback handler used to authenticate a remote endpoint by default.
     *
     * @return the callback handler
     */
    CallbackHandler getRemoteCallbackHandler();

    /**
     * Change the callback handler used to authenticate a remote endpoint by default.
     *
     * @param callbackHandler the callback handler
     */
    void setRemoteCallbackHandler(CallbackHandler callbackHandler);

    /**
     * Get the callback handler used to authenticate this endpoint to a remote endpoint by default.
     *
     * @return the callback handler
     */
    CallbackHandler getLocalCallbackHandler();

    /**
     * Change the callback handler used to authenticate this endpoint to a remote endpoint by default.
     *
     * @param callbackHandler the callback handler
     */
    void setLocalCallbackHandler(CallbackHandler callbackHandler);
}
