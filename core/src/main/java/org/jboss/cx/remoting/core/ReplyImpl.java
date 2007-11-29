package org.jboss.cx.remoting.core;

import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.core.util.CollectionUtil;

/**
 *
 */
public final class ReplyImpl<T> extends AbstractBasicMessage<T> implements Reply<T> {
    protected ReplyImpl(final T body) {
        super(body);
    }
}
