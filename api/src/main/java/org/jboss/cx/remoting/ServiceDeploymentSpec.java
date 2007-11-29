package org.jboss.cx.remoting;

import java.util.List;
import org.jboss.cx.remoting.spi.InterceptorSpec;
import org.jboss.cx.remoting.core.util.CollectionUtil;

/**
 *
 */
public final class ServiceDeploymentSpec<I, O> {
    private final List<InterceptorSpec> interceptorSpecs;
    private final String serviceName;
    private final Class<I> requestType;
    private final Class<O> replyType;
    private final RequestListener<I, O> requestListener;

    public static final ServiceDeploymentSpec<Void, Void> DEFAULT = new ServiceDeploymentSpec<Void, Void>(null, null, Void.class, Void.class, null);

    private ServiceDeploymentSpec(final List<InterceptorSpec> interceptorSpecs, final String serviceName, final Class<I> requestType, final Class<O> replyType, final RequestListener<I, O> requestListener) {
        this.interceptorSpecs = interceptorSpecs;
        this.serviceName = serviceName;
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
        return new ServiceDeploymentSpec<I, O>(CollectionUtil.unmodifiableList(specs.clone()), serviceName, requestType, replyType, requestListener);
    }

    public ServiceDeploymentSpec<I, O> setServiceName(String serviceName) {
        return new ServiceDeploymentSpec<I, O>(interceptorSpecs, serviceName, requestType, replyType, requestListener);
    }

    public <T> ServiceDeploymentSpec<T, O> setRequestType(Class<T> requestType) {
        return new ServiceDeploymentSpec<T, O>(interceptorSpecs, serviceName, requestType, replyType, null);
    }

    public <T> ServiceDeploymentSpec<I, T> setReplyType(Class<T> replyType) {
        return new ServiceDeploymentSpec<I, T>(interceptorSpecs, serviceName, requestType, replyType, null);
    }

    public ServiceDeploymentSpec<I, O> setRequestListener(RequestListener<I, O> requestListener) {
        return new ServiceDeploymentSpec<I, O>(interceptorSpecs, serviceName, requestType, replyType, requestListener);
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Service specification for ");
        if (serviceName == null) {
            builder.append("unnamed service ");
        } else {
            builder.append("service \"");
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
