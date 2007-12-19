package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.Request;

/**
 *
 */
public final class RequestImpl<T> extends AbstractBasicMessage<T> implements Request<T> {
    protected RequestImpl(final T body) {
        super(body);
    }
}
