package org.jboss.cx.remoting.core;

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

/**
 *
 */
public final class ServiceLocatorListener<I, O> implements RequestListener<ServiceRequest<I, O>, ServiceReply<I, O>> {

    private interface Peer {
        String getName();

        int getCost();

        <X, Y> Context<ServiceRequest<X, Y>, ServiceReply<X, Y>> getLocatorContext();
    }

    private static <K, V> ConcurrentMap<K, V> syncMap() {
        return CollectionUtil.concurrentMap(CollectionUtil.<K, V>hashMap());
    }

    private final ConcurrentMap<String, ConcurrentMap<String, ContextSource<?, ?>>> deployments = syncMap();

    public void handleRequest(final RequestContext<ServiceReply<I, O>> requestContext, final ServiceRequest<I, O> request) throws RemoteExecutionException, InterruptedException {
        final URI uri = request.getUri();
        final ServiceURI serviceURI = new ServiceURI(uri);
        final String endpointName = serviceURI.getEndpointName();
        final String groupName = serviceURI.getGroupName();
        final String serviceType = serviceURI.getServiceType();

        
    }
}
