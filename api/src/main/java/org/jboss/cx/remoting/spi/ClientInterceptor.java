package org.jboss.cx.remoting.spi;

import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.RemoteExecutionException;

/**
 * An interceptor that provides an additional service to a {@code Context}.  A context service interceptor is created
 * for every context service for each context.  Upon creation, the interceptors are tied together using the {@code
 * setNext}/{@code setPrevious} methods.  Afterwards, the {@code processXXX} methods are invoked to handle data coming
 * in or going out through the context.
 * <p/>
 * The interceptor {@code processXXX} methods are expected to delegate to the next or previous interceptor after
 * performing the required processing.  This diagram illustrates the relationship between interceptors and the Remoting
 * core: <p><img src="Interceptors.png" alt="Diagram depicting the relationship between interceptors and the Remoting
 * core"/></p>
 * <p/>
 * The general rule is that outbound process methods delegate to the next handler, and inbound process methods delegate
 * to the previous handler.  The methods may make exceptions in certain circumstances, as described in the method
 * documentation, in order to "short-circuit" the request mechanism or to affect message delivery in a service-specific
 * way.
 * <p/>
 * The methods {@code processOutboundRequest}, {@code processOutboundMessage}, {@code processInboundReply}, and {@code
 * processInboundException} are all executed on the requesting ("client") side of the context.
 */
public interface ClientInterceptor {
    /**
     * Set the next context service handler.  When requests are processed, each handler delegates to the next handler in
     * the chain.  Called once after the context service hander is created.
     *
     * @param nextInterceptor the next interceptor
     */
    void setNext(ClientInterceptor nextInterceptor);

    /**
     * Set the previous context service handler.  When replies are processed, each handler delegates to the previous
     * handler in the chain.  Called once after the context service hander is created.
     *
     * @param previousInterceptor the previous interceptor
     */
    void setPrevious(ClientInterceptor previousInterceptor);

    /**
     * Get the context service object associated with this handler.  This instance is the end-user's interface into this
     * service.  If no interface is available for this context service, return {@code null}.
     *
     * @return the context service object
     */
    <T extends ContextService> T getContextService(InterceptorContext context);

    /**
     * Process an outbound request.
     *
     * @param context the context service interceptor context
     * @param requestIdentifier the request identifier
     * @param request the outbound request
     */
    void processOutboundRequest(InterceptorContext context, RequestIdentifier requestIdentifier, Request<?> request);

    /**
     * Process an inbound request reply.
     *
     * @param context the context service interceptor context
     * @param requestIdentifier the request identifier
     * @param reply the inbound reply
     */
    void processInboundReply(InterceptorContext context, RequestIdentifier requestIdentifier, Reply<?> reply);

    /**
     * Process an inbound request exception.
     *
     * @param context the context service interceptor context
     * @param requestIdentifier the request identifier
     * @param exception the inbound exception
     */
    void processInboundException(InterceptorContext context, RequestIdentifier requestIdentifier, RemoteExecutionException exception);

    /**
     * Process an outbound cancellation request.
     *
     * @param context the context service interceptor context
     * @param requestIdentifier the request identifier
     * @param mayInterrupt {@code true} if the operation can be interrupted
     */
    void processOutboundCancelRequest(InterceptorContext context, RequestIdentifier requestIdentifier, boolean mayInterrupt);

    /**
     * Process an inbound cancellation acknowledgement.
     *
     * @param context the context service interceptor context
     * @param requestIdentifier the request identifier
     */
    void processInboundCancelAcknowledge(InterceptorContext context, RequestIdentifier requestIdentifier);

    /**
     * Close this interceptor.  The handler MUST subsequently close the NEXT interceptor in the chain (i.e. in a {@code
     * finally} block).  The handler may not access the previous interceptor in the chain, since it will already have
     * been closed.
     */
    void close();
}
