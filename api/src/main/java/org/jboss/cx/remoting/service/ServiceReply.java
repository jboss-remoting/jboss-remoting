package org.jboss.cx.remoting.service;

import java.io.Serializable;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.ClientSource;

/**
 *
 */
public final class ServiceReply<I, O> implements Serializable {
    private static final long serialVersionUID = 1L;

    private ClientSource<I, O> serviceClientSource;
    private Client<ClassLoaderResourceRequest, ClassLoaderResourceReply> classLoadingClient;

    public ServiceReply() {
    }

    public ClientSource<I, O> getServiceContextSource() {
        return serviceClientSource;
    }

    public void setServiceContextSource(final ClientSource<I, O> serviceClientSource) {
        this.serviceClientSource = serviceClientSource;
    }

    public Client<ClassLoaderResourceRequest, ClassLoaderResourceReply> getClassLoadingContext() {
        return classLoadingClient;
    }

    public void setClassLoadingContext(final Client<ClassLoaderResourceRequest, ClassLoaderResourceReply> classLoadingClient) {
        this.classLoadingClient = classLoadingClient;
    }
}
