package org.jboss.cx.remoting.spi;

/**
 * A simple base implementation of {@code ContextServiceInterceptor}.  Use this class as a base for simple
 * implementations of that interface.
 */
public abstract class AbstractClientInterceptor<T> extends AbstractInterceptor implements ClientInterceptor<T> {
    protected AbstractClientInterceptor() {
    }

    public T getContextService(InterceptorContext context) {
        return null;
    }
}
