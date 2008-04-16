package org.jboss.cx.remoting;

/**
 *
 */
public abstract class AbstractRequestListener<I, O> implements RequestListener<I, O> {
    public void handleClientOpen(final ClientContext context) {
    }

    public void handleServiceOpen(final ServiceContext context) {
    }

    public void handleServiceClose(final ServiceContext context) {
    }

    public void handleClientClose(final ClientContext context) {
    }
}
