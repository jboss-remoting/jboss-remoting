package org.jboss.cx.remoting.core;

import java.util.concurrent.ConcurrentMap;
import java.util.List;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.spi.ServerInterceptorFactory;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 *
 */
public final class CoreInboundContext<I, O> {

    private final ContextIdentifier contextIdentifier;
    private final CoreSession coreSession;
    private final RequestListener<I, O> requestListener;

    private final ConcurrentMap<RequestIdentifier,CoreInboundRequest<I, O>> requests = CollectionUtil.concurrentMap();

    public CoreInboundContext(final ContextIdentifier contextIdentifier, final CoreSession coreSession, final RequestListener<I, O> requestListener, final List<ServerInterceptorFactory> factoryList) {
        this.contextIdentifier = contextIdentifier;
        this.coreSession = coreSession;
        this.requestListener = requestListener;
    }

    // Outbound protocol messages

    void sendReply(final RequestIdentifier requestIdentifier, final Reply<O> reply) throws RemotingException {
        coreSession.sendReply(contextIdentifier, requestIdentifier, reply);
    }

    void sendException(final RequestIdentifier requestIdentifier, final RemoteExecutionException cause) throws RemotingException {
        coreSession.sendException(contextIdentifier, requestIdentifier, cause);
    }

    void sendCancelAcknowledge(final RequestIdentifier requestIdentifier) throws RemotingException {
        coreSession.sendCancelAcknowledge(contextIdentifier, requestIdentifier);
    }

    // Inbound protocol messages

    void receiveCancelRequest(final RequestIdentifier requestIdentifier, final boolean mayInterrupt) {
        final CoreInboundRequest<I, O> inboundRequest = getInboundRequest(requestIdentifier);
        if (inboundRequest != null) {
            inboundRequest.receiveCancelRequest(mayInterrupt);
        }
    }

    void receiveRequest(final RequestIdentifier requestIdentifier, final Request<I> request) {
        final CoreInboundRequest<I, O> inboundRequest = createInboundRequest(requestIdentifier, request);
        inboundRequest.receiveRequest(request);
    }

    // Other protocol-related

    protected void shutdown() {
        
    }

    // Request mgmt

    CoreInboundRequest<I, O> createInboundRequest(final RequestIdentifier requestIdentifier, final Request<I> request) {
        return new CoreInboundRequest<I, O>(requestIdentifier, request, this, requestListener);
    }

    CoreInboundRequest<I, O> getInboundRequest(RequestIdentifier requestIdentifier) {
        return requests.get(requestIdentifier);
    }
}
