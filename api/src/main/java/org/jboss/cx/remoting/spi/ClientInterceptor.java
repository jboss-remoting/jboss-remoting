package org.jboss.cx.remoting.spi;

/**
 * An interceptor that provides an additional service to a {@code Context}.  A context service interceptor is created
 * for every context service for each context.
 */
public interface ClientInterceptor<T> extends Interceptor {

    /**
     * Get the context service object associated with this handler.  This instance is the end-user's interface into this
     * service.  If no interface is available for this context service, return {@code null}.
     *
     * @return the context service object
     */
    T getContextService(InterceptorContext context);
}
