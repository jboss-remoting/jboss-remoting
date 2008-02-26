package org.jboss.cx.remoting;

/**
 *
 */
public interface CloseHandler<T> {
    void handleClose(T closed);
}
