package org.jboss.cx.remoting.core.service;

import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.ContextSource;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.util.ServiceURI;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.service.ServiceRequest;
import org.jboss.cx.remoting.service.ServiceReply;
import java.net.URI;
import java.util.concurrent.ConcurrentMap;
import java.util.SortedMap;

/**
 *
 */
public final class ServiceLocatorListener<I, O> implements RequestListener<ServiceRequest<I, O>, ServiceReply<I, O>> {

    private interface Service {
        String getGroupName();

        String getType();

        // todo - add in whatever negotation to the request object (security?)
        <X, Y> Context<Void, ServiceReply<X, Y>> getServiceChannel();
    }

    private interface Peer {
        String getName();

        int getCost();

        <X, Y> Context<ServiceRequest<X, Y>, ServiceReply<X, Y>> getLocatorContext();

        SortedMap<String, Service> getServicesByGroupName();

        SortedMap<String, Service> getServicesByType();
    }

    private static <K, V> ConcurrentMap<K, V> syncMap() {
        return CollectionUtil.synchronizedMap(CollectionUtil.<K, V>hashMap());
    }

    private final ConcurrentMap<String, ConcurrentMap<String, ContextSource<?, ?>>> deployments = syncMap();

    public void handleOpen() {
    }

    public void handleRequest(final RequestContext<ServiceReply<I, O>> requestContext, final ServiceRequest<I, O> request) throws RemoteExecutionException, InterruptedException {
        final URI uri = request.getUri();
        final ServiceURI serviceURI = new ServiceURI(uri);
        final String endpointName = serviceURI.getEndpointName();
        final String groupName = serviceURI.getGroupName();
        final String serviceType = serviceURI.getServiceType();

        
    }

    public void handleClose() {
    }


}
