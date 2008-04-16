package org.jboss.cx.remoting.core.service;

import java.net.URI;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.AbstractRequestListener;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.ClientSource;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.service.ServiceReply;
import org.jboss.cx.remoting.service.ServiceRequest;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.util.ServiceURI;

/**
 *
 */
public final class ServiceLocatorListener<I, O> extends AbstractRequestListener<ServiceRequest<I, O>,ServiceReply<I, O>> {

    private interface Service {
        String getGroupName();

        String getType();

        // todo - add in whatever negotation to the request object (security?)
        <X, Y> Client<Void, ServiceReply<X, Y>> getServiceChannel();
    }

    private interface Peer {
        String getName();

        int getCost();

        <X, Y> Client<ServiceRequest<X, Y>, ServiceReply<X, Y>> getLocatorClient();

        SortedMap<String, Service> getServicesByGroupName();

        SortedMap<String, Service> getServicesByType();
    }

    private static <K, V> ConcurrentMap<K, V> syncMap() {
        return CollectionUtil.synchronizedMap(CollectionUtil.<K, V>hashMap());
    }

    private final ConcurrentMap<String, ConcurrentMap<String, ClientSource<?, ?>>> deployments = syncMap();

    public void handleRequest(final RequestContext<ServiceReply<I, O>> requestContext, final ServiceRequest<I, O> request) throws RemoteExecutionException, InterruptedException {
        final URI uri = request.getUri();
        final ServiceURI serviceURI = new ServiceURI(uri);
        final String endpointName = serviceURI.getEndpointName();
        final String groupName = serviceURI.getGroupName();
        final String serviceType = serviceURI.getServiceType();
    }
}
