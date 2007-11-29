package org.jboss.cx.remoting.spi;

import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public interface RequestListenerFactory {
    <I, O> RequestListener<I, O> createListener(ListenerFactoryContext context, Class<I> requestType, Class<O> replyType) throws RemotingException;
}
