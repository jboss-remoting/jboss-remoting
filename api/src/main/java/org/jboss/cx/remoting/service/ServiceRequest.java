package org.jboss.cx.remoting.service;

import java.net.URI;
import java.io.Serializable;

/**
 *
 */
public final class ServiceRequest<I, O> implements Serializable {
    private static final long serialVersionUID = 1L;

    private URI uri;
    private Class<I> requestType;
    private Class<O> replyType;

    public ServiceRequest() {
    }

    public static <I, O> ServiceRequest<I, O> create(Class<I> requestType, Class<O> replyType, URI uri) {
        ServiceRequest<I, O> serviceRequest = new ServiceRequest<I, O>();
        serviceRequest.setRequestType(requestType);
        serviceRequest.setReplyType(replyType);
        serviceRequest.setUri(uri);
        return serviceRequest;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(final URI uri) {
        this.uri = uri;
    }

    public Class<I> getRequestType() {
        return requestType;
    }

    public void setRequestType(final Class<I> requestType) {
        this.requestType = requestType;
    }

    public Class<O> getReplyType() {
        return replyType;
    }

    public void setReplyType(final Class<O> replyType) {
        this.replyType = replyType;
    }
}
