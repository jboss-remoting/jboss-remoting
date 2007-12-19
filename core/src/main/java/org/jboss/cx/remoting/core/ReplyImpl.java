package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.Reply;

/**
 *
 */
public final class ReplyImpl<T> extends AbstractBasicMessage<T> implements Reply<T> {
    protected ReplyImpl(final T body) {
        super(body);
    }
}
