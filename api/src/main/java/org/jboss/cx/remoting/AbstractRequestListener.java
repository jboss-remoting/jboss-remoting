package org.jboss.cx.remoting;

/**
 * A simple request listener implementation that implements all methods with no-operation implementations.
 */
public abstract class AbstractRequestListener<I, O> implements RequestListener<I, O> {

    /**
     * {@inheritDoc}  This implementation performs no operation.
     */
    public void handleClientOpen(final ClientContext context) {
    }

    /**
     * {@inheritDoc}  This implementation performs no operation.
     */
    public void handleServiceOpen(final ServiceContext context) {
    }

    /**
     * {@inheritDoc}  This implementation performs no operation.
     */
    public void handleServiceClose(final ServiceContext context) {
    }

    /**
     * {@inheritDoc}  This implementation performs no operation.
     */
    public void handleClientClose(final ClientContext context) {
    }
}
