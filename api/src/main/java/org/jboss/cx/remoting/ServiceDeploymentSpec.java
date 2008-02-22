package org.jboss.cx.remoting;

import java.util.Collections;
import java.util.List;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.spi.InterceptorSpec;

/**
 *
 */
public final class ServiceDeploymentSpec<I, O> {
    private final List<InterceptorSpec> interceptorSpecs;
    private final String serviceName;
    private final String serviceType;
    private final Class<I> requestType;
    private final Class<O> replyType;
    private final RequestListener<I, O> requestListener;

    public static final ServiceDeploymentSpec<Void, Void> DEFAULT = new ServiceDeploymentSpec<Void, Void>(Collections.<InterceptorSpec>emptyList(), null, null, Void.class, Void.class, null);

    private ServiceDeploymentSpec(final List<InterceptorSpec> interceptorSpecs, final String serviceName, final String serviceType, final Class<I> requestType, final Class<O> replyType, final RequestListener<I, O> requestListener) {
        this.interceptorSpecs = interceptorSpecs;
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.requestType = requestType;
        this.replyType = replyType;
        this.requestListener = requestListener;
    }

    public List<InterceptorSpec> getInterceptorSpecs() {
        return interceptorSpecs;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public Class<I> getRequestType() {
        return requestType;
    }

    public Class<O> getReplyType() {
        return replyType;
    }

    public RequestListener<I, O> getRequestListener() {
        return requestListener;
    }

    public ServiceDeploymentSpec<I, O> setInterceptorSpecs(InterceptorSpec... specs) {
        if (specs == null) {
            throw new NullPointerException("specs is null");
        }
        return new ServiceDeploymentSpec<I, O>(CollectionUtil.unmodifiableList(specs.clone()), serviceName, serviceType, requestType, replyType, requestListener);
    }

    public ServiceDeploymentSpec<I, O> setServiceGroupName(String serviceName) {
        if (serviceName == null) {
            throw new NullPointerException("serviceName is null");
        }
        return new ServiceDeploymentSpec<I, O>(interceptorSpecs, serviceName, serviceType, requestType, replyType, requestListener);
    }

    public ServiceDeploymentSpec<I, O> setServiceType(final String serviceType) {
        if (serviceType == null) {
            throw new NullPointerException("serviceType is null");
        }
        return new ServiceDeploymentSpec<I, O>(interceptorSpecs, serviceName, serviceType, requestType, replyType, requestListener);
    }

    public <T> ServiceDeploymentSpec<T, O> setRequestType(Class<T> requestType) {
        if (requestType == null) {
            throw new NullPointerException("requestType is null");
        }
        return new ServiceDeploymentSpec<T, O>(interceptorSpecs, serviceName, serviceType, requestType, replyType, null);
    }

    public <T> ServiceDeploymentSpec<I, T> setReplyType(Class<T> replyType) {
        if (replyType == null) {
            throw new NullPointerException("replyType is null");
        }
        return new ServiceDeploymentSpec<I, T>(interceptorSpecs, serviceName, serviceType, requestType, replyType, null);
    }

    public ServiceDeploymentSpec<I, O> setRequestListener(RequestListener<I, O> requestListener) {
        if (requestListener == null) {
            throw new NullPointerException("requestListener is null");
        }
        return new ServiceDeploymentSpec<I, O>(interceptorSpecs, serviceName, serviceType, requestType, replyType, requestListener);
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Service specification for ");
        if (serviceType == null) {
            builder.append("untyped ");
        } else {
            builder.append("typed (\"");
            builder.append(serviceType);
            builder.append("\") ");
        }
        if (serviceName == null) {
            builder.append(", unnamed service ");
        } else {
            builder.append("service named \"");
            builder.append(serviceName);
            builder.append("\" ");
        }
        builder.append(": Request type is ");
        builder.append(requestType.getName());
        builder.append(", reply type is ");
        builder.append(replyType.getName());
        builder.append(", interceptors are ");
        builder.append(interceptorSpecs.toString());
        builder.append(", request listener is ");
        builder.append(requestListener.toString());
        return builder.toString();
    }
}
