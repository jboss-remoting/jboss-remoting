package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.util.ServiceURI;
import org.jboss.cx.remoting.service.ServiceRequest;
import org.jboss.cx.remoting.service.ServiceReply;
import java.net.URI;

/**
 *
 */
public final class ServiceLocatorListener<I, O> implements RequestListener<ServiceRequest<I, O>, ServiceReply<I, O>> {
    

    public void handleRequest(final RequestContext<ServiceReply<I, O>> requestContext, final ServiceRequest<I, O> request) throws RemoteExecutionException, InterruptedException {
        final URI uri = request.getUri();
        final ServiceURI serviceURI = new ServiceURI(uri);
        final String endpointName = serviceURI.getEndpointName();
        final String groupName = serviceURI.getGroupName();
        final String serviceType = serviceURI.getServiceType();
        
    }
}
