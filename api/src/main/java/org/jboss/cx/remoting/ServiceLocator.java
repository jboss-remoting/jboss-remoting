package org.jboss.cx.remoting;

import org.jboss.cx.remoting.spi.RequestListenerFactory;

/**
 *
 */
public final class ServiceLocator<I, O> {

    /**
     * A default interceptor policy callback.  The default policy will accept any supported interceptor requested by the
     * remote side.
     */
    public static final InterceptorPolicyCallback DEFAULT_INTERCEPTOR_POLICY_CALLBACK = new InterceptorPolicyCallback() {
        public boolean isAllowed(String interceptorName, int slot) {
            return true;
        }
    };

    /**
     * A basic service locator.  Use this instance to create more specific locators.
     */
    public static final ServiceLocator<Void, Void> DEFAULT = new ServiceLocator<Void, Void>(Void.class, Void.class, null, "*", "*", DEFAULT_INTERCEPTOR_POLICY_CALLBACK, null);

    private final Class<I> requestType;
    private final Class<O> replyType;
    private final String serviceType;
    private final String serviceMemberName;
    private final String serviceGroupMemberName;
    private final InterceptorPolicyCallback interceptorPolicyCallback;
    private final RequestListenerFactory requestListenerFactory;

    private ServiceLocator(final Class<I> requestType, final Class<O> replyType, final String serviceType, final String serviceMemberName, final String serviceGroupMemberName, final InterceptorPolicyCallback interceptorPolicyCallback, final RequestListenerFactory requestListenerFactory) {
        if (requestType == null) {
            throw new NullPointerException("requestType is null");
        }
        if (replyType == null) {
            throw new NullPointerException("replyType is null");
        }
        if (interceptorPolicyCallback == null) {
            throw new NullPointerException("interceptorPolicyCallback is null");
        }
        this.requestType = requestType;
        this.replyType = replyType;
        this.serviceType = serviceType;
        this.serviceMemberName = serviceMemberName;
        this.serviceGroupMemberName = serviceGroupMemberName;
        this.interceptorPolicyCallback = interceptorPolicyCallback;
        this.requestListenerFactory = requestListenerFactory;
    }

    /**
     * Get the request type for this service locator.  The remote service will accept this request type if the actual
     * service accepts this type, or a superclass or superinterface thereof.
     *
     * @return the request type
     */
    public Class<I> getRequestType() {
        return requestType;
    }

    /**
     * Get the reply type for this service locator.  The remote service will accept this reply type if the actual
     * service returns this type, or a subtype thereof.
     *
     * @return the reply type
     */
    public Class<O> getReplyType() {
        return replyType;
    }

    /**
     * Get the name of the service for this service locator.
     *
     * @return the service name
     */
    public String getServiceMemberName() {
        return serviceMemberName;
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
     * Get the name of the service group for this service locator.
     *
     * @return the service group name
     */
    public String getServiceGroupMemberName() {
        return serviceGroupMemberName;
    }

    /**
     * Get the local interceptor policy for this service locator.  By default, all required interceptors are accepted,
     * and all optional interceptors supported by the endpoint are accepted.
     *
     * @return the interceptor policy
     */
    public InterceptorPolicyCallback getInterceptorPolicy() {
        return interceptorPolicyCallback;
    }

    /**
     * Get the request listener factory for return requests from this service.
     * <p/>
     * todo - do we really want this symmetry
     *
     * @return the request listener factory
     */
    public RequestListenerFactory getRequestListenerFactory() {
        return requestListenerFactory;
    }

    /**
     * Change the request type.  This method does not modify this object; instead, it returns a new modified instance.
     *
     * @param requestType the new request type
     *
     * @return an updated service locator
     */
    public <T> ServiceLocator<T, O> setRequestType(Class<T> requestType) {
        return new ServiceLocator<T, O>(requestType, replyType, serviceType, serviceMemberName, serviceGroupMemberName, interceptorPolicyCallback, requestListenerFactory);
    }

    /**
     * Change the request type.  This method does not modify this object; instead, it returns a new modified instance.
     *
     * @param replyType the new request type
     *
     * @return an updated service locator
     */
    public <T> ServiceLocator<I, T> setReplyType(Class<T> replyType) {
        return new ServiceLocator<I, T>(requestType, replyType, serviceType, serviceMemberName, serviceGroupMemberName, interceptorPolicyCallback, requestListenerFactory);
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
        return new ServiceLocator<I, O>(requestType, replyType, serviceType, serviceMemberName, serviceGroupMemberName, interceptorPolicyCallback, requestListenerFactory);
    }

    /**
     * Change the service group member name.  The service group member name is an (optional) name that identifies the
     * individual member within a service group.  If you have more than one instance within a service group, the service
     * group member  name can be used to disambiguate them.  For example, if there are two instances running in a
     * cluster, you might have services with the same type named "node1" and "node2".
     * <p/>
     * The service group member name should be a dot-separated name (like an Internet host name).  A {@code "*"}
     * character can be used as a wildcard to match any name.  So, the name {@code "foo.*"} would match {@code
     * "foo.bar"} and {@code "foo.bar.two"} but not {@code "foobar"}.
     * <p/>
     * If no service name is specified, then this value defaults to {@code "*"} (match all names).
     * <p/>
     * This method does not modify this object; instead, it returns a new modified instance.
     *
     * @param serviceGroupMemberName the new service group member name; may not be {@code null}
     *
     * @return an updated service locator
     */
    public ServiceLocator<I, O> setServiceGroupMemberName(String serviceGroupMemberName) {
        if (serviceGroupMemberName == null) {
            throw new NullPointerException("serviceGroupMemberName is null");
        }
        return new ServiceLocator<I, O>(requestType, replyType, serviceType, serviceGroupMemberName, this.serviceGroupMemberName, interceptorPolicyCallback, requestListenerFactory);
    }

    /**
     * Change the service group name.  The service group name is a string that identifies a group of endpoints that are
     * all providing the same service, for load-balancing or clustering purposes.
     *
     * @param serviceGroupName
     *
     * @return
     */
    public ServiceLocator<I, O> setServiceGroupName(String serviceGroupName) {
        if (serviceGroupName == null) {
            throw new NullPointerException("serviceGroupName is null");
        }
        return new ServiceLocator<I, O>(requestType, replyType, serviceType, serviceMemberName, serviceGroupName, interceptorPolicyCallback, requestListenerFactory);
    }

    /**
     * Change the interceptor policy callback.  This method does not modify this object; instead, it returns a new
     * modified instance.
     *
     * @param interceptorPolicyCallback the new callback
     *
     * @return an updated service locator
     */
    public ServiceLocator<I, O> setInterceptorPolicy(InterceptorPolicyCallback interceptorPolicyCallback) {
        return new ServiceLocator<I, O>(requestType, replyType, serviceType, serviceMemberName, serviceGroupMemberName, interceptorPolicyCallback, requestListenerFactory);
    }

    /**
     * Change the request listener factory.  This method does not modify this object; instead, it returns a new modified
     * instance.
     * <p/>
     * todo - do we really want this symmetry
     *
     * @param requestListenerFactory the new request listener factory
     *
     * @return an updated service locator
     */
    public ServiceLocator<I, O> setRequestListenerFactory(RequestListenerFactory requestListenerFactory) {
        return new ServiceLocator<I, O>(requestType, replyType, serviceType, serviceMemberName, serviceGroupMemberName, interceptorPolicyCallback, requestListenerFactory);
    }
}
