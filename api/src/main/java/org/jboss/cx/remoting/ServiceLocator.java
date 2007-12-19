package org.jboss.cx.remoting;

import java.util.Collections;
import java.util.Set;

/**
 *
 */
public final class ServiceLocator<I, O> {

    /**
     * A basic service locator.  Use this instance to create more specific locators.
     */
    public static final ServiceLocator<Void, Void> DEFAULT = new ServiceLocator<Void, Void>(Void.class, Void.class, null, "*", "*", Collections.<String>emptySet());

    private final Class<I> requestType;
    private final Class<O> replyType;
    private final String serviceType;
    private final String serviceGroupName;
    private final String endpointName;
    private final Set<String> availableInterceptors;

    private ServiceLocator(final Class<I> requestType, final Class<O> replyType, final String serviceType, final String serviceGroupName, final String endpointName, final Set<String> availableInterceptors) {
        if (requestType == null) {
            throw new NullPointerException("requestType is null");
        }
        if (replyType == null) {
            throw new NullPointerException("replyType is null");
        }
        if (availableInterceptors == null) {
            throw new NullPointerException("availableInterceptors is null");
        }
        this.requestType = requestType;
        this.replyType = replyType;
        this.serviceType = serviceType;
        this.serviceGroupName = serviceGroupName;
        this.endpointName = endpointName;
        this.availableInterceptors = availableInterceptors;
    }

    /**
     * Get the request type for this service locator.  The remote service will match this request type if the actual
     * service accepts this type, or a superclass or superinterface thereof.
     *
     * @return the request type
     */
    public Class<I> getRequestType() {
        return requestType;
    }

    /**
     * Get the reply type for this service locator.  The remote service will match this reply type if the actual
     * service returns this type, or a subtype thereof.
     *
     * @return the reply type
     */
    public Class<O> getReplyType() {
        return replyType;
    }

    /**
     * Get the name of the service group for this service locator.
     *
     * @return the service group name
     */
    public String getServiceGroupName() {
        return serviceGroupName;
    }

    /**
     * Get the type of the service for this service locator.
     *
     * @return the service type
     */
    public String getServiceType() {
        return serviceType;
    }

    /**
     * Get the name of the endpoitn for this service locator.
     *
     * @return the endpoint name
     */
    public String getEndpointName() {
        return endpointName;
    }

    /**
     * Get the names of the interceptors that the client has available.
     *
     * @return the names
     */
    public Set<String> getAvailableInterceptors() {
        return availableInterceptors;
    }

    /**
     * Change the request type.  This method does not modify this object; instead, it returns a new modified instance.
     *
     * @param requestType the new request type
     *
     * @return an updated service locator
     */
    public <T> ServiceLocator<T, O> setRequestType(Class<T> requestType) {
        return new ServiceLocator<T, O>(requestType, replyType, serviceType, serviceGroupName, endpointName, availableInterceptors);
    }

    /**
     * Change the request type.  This method does not modify this object; instead, it returns a new modified instance.
     *
     * @param replyType the new request type
     *
     * @return an updated service locator
     */
    public <T> ServiceLocator<I, T> setReplyType(Class<T> replyType) {
        return new ServiceLocator<I, T>(requestType, replyType, serviceType, serviceGroupName, endpointName, availableInterceptors);
    }

    /**
     * Change the service type.  The service type is a string that identifies the "kind" of service provided.  All
     * services of a given type should accept the same request and reply types as well.
     * <p/>
     * The service type should be a dot-separated name (like an Internet host name).
     * <p/>
     * This method does not modify this object; instead, it returns a new modified instance.
     *
     * @param serviceType the new service type; may not be {@code null}
     *
     * @return an updated service locator
     */
    public ServiceLocator<I, O> setServiceType(String serviceType) {
        if (serviceType == null) {
            throw new NullPointerException("serviceType is null");
        }
        return new ServiceLocator<I, O>(requestType, replyType, serviceType, serviceGroupName, endpointName, availableInterceptors);
    }

    /**
     * Change the service group name.  The service group name is a string that identifies a group of endpoints that are
     * all providing the same service, for load-balancing or clustering purposes.
     *
     * @param serviceGroupName
     *
     * @return an updated service locator
     */
    public ServiceLocator<I, O> setServiceGroupName(String serviceGroupName) {
        if (serviceGroupName == null) {
            throw new NullPointerException("serviceGroupName is null");
        }
        return new ServiceLocator<I, O>(requestType, replyType, serviceType, serviceGroupName, endpointName, availableInterceptors);
    }

    /**
     * Change the endpoint name.
     * <p/>
     * The endpoint name should be a dot-separated name (like an Internet host name).  A {@code "*"}
     * character can be used as a wildcard to match any name.  So, the name {@code "foo.*"} would match {@code
     * "foo.bar"} and {@code "foo.bar.two"} but not {@code "foobar"}.
     * <p/>
     * If no endpoint name is specified, then this value defaults to {@code "*"} (match all endpoints).
     * <p/>
     * This method does not modify this object; instead, it returns a new modified instance.
     *
     * @param endpointName the new endpoint name; may not be {@code null}
     *
     * @return an updated service locator
     */
    public ServiceLocator<I, O> setEndpointName(String endpointName) {
        if (endpointName == null) {
            throw new NullPointerException("endpointName is null");
        }
        return new ServiceLocator<I, O>(requestType, replyType, serviceType, serviceGroupName, endpointName, availableInterceptors);
    }

    /**
     * Change the set of locally available interceptors.
     *
     * @param availableInterceptors the set of interceptors
     *
     * @return an updated service locator
     */
    public ServiceLocator<I, O> setAvailableInterceptors(Set<String> availableInterceptors) {
        if (availableInterceptors == null) {
            throw new NullPointerException("availableInterceptors is null");
        }
        return new ServiceLocator<I, O>(requestType, replyType, serviceType, serviceGroupName, endpointName, availableInterceptors);
    }

}
