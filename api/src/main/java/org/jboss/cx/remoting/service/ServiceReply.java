package org.jboss.cx.remoting.service;

import java.io.Serializable;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.ContextSource;

/**
 *
 */
public final class ServiceReply<I, O> implements Serializable {
    private static final long serialVersionUID = 1L;

    private ContextSource<I, O> serviceContextSource;
    private Context<ClassLoaderResourceRequest, ClassLoaderResourceReply> classLoadingContext;

    public ServiceReply() {
    }

    public ContextSource<I, O> getServiceContextSource() {
        return serviceContextSource;
    }

    public void setServiceContextSource(final ContextSource<I, O> serviceContextSource) {
        this.serviceContextSource = serviceContextSource;
    }

    public Context<ClassLoaderResourceRequest, ClassLoaderResourceReply> getClassLoadingContext() {
        return classLoadingContext;
    }

    public void setClassLoadingContext(final Context<ClassLoaderResourceRequest, ClassLoaderResourceReply> classLoadingContext) {
        this.classLoadingContext = classLoadingContext;
    }
}
