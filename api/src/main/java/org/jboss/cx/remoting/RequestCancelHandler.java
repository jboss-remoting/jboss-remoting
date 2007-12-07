package org.jboss.cx.remoting;

/**
 *
 */
public interface RequestCancelHandler<O> {
    void notifyCancel(RequestContext<O> requestContext);
}
