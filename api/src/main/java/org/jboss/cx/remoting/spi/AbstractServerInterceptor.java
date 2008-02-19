package org.jboss.cx.remoting.spi;

/**
 * A simple base implementation of {@code ContextServiceInterceptor}.  Use this class as a base for simple
 * implementations of that interface.
 */
public abstract class AbstractServerInterceptor extends AbstractInterceptor implements ServerInterceptor {
    protected AbstractServerInterceptor() {
    }
}
