package org.jboss.cx.remoting;

/**
 *
 */
public abstract class AbstractRequestListener<I, O> implements RequestListener<I, O> {
    public void handleContextOpen(final ContextContext context) {
    }

    public void handleServiceOpen(final ServiceContext context) {
    }

    public void handleServiceClose(final ServiceContext context) {
    }

    public void handleContextClose(final ContextContext context) {
    }
}
