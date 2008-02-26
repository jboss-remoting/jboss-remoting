package org.jboss.cx.remoting.service;

import org.jboss.cx.remoting.ContextSource;
import org.jboss.cx.remoting.Context;
import java.io.Serializable;

/**
 *
 */
public final class ServiceReply<I, O> implements Serializable {
    private static final long serialVersionUID = 1L;

    private ContextSource<I, O> serviceContextSource;
    private Context<ClassRequest, ClassReply> classLoadingContext;

    public ServiceReply() {
    }

    public ContextSource<I, O> getServiceContextSource() {
        return serviceContextSource;
    }

    public void setServiceContextSource(final ContextSource<I, O> serviceContextSource) {
        this.serviceContextSource = serviceContextSource;
    }

    public Context<ClassRequest, ClassReply> getClassLoadingContext() {
        return classLoadingContext;
    }

    public void setClassLoadingContext(final Context<ClassRequest, ClassReply> classLoadingContext) {
        this.classLoadingContext = classLoadingContext;
    }
}
