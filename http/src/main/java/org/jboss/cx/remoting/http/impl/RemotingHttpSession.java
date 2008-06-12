package org.jboss.cx.remoting.http.impl;

import java.util.Queue;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.util.ByteMessageInput;
import org.jboss.cx.remoting.http.RemotingHttpChannelContext;
import org.jboss.cx.remoting.http.HttpMessageWriter;

/**
 *
 */
public final class RemotingHttpSession {
    private final Queue<Action> outboundQueue = CollectionUtil.linkedList();

    private final class ChannelContext implements RemotingHttpChannelContext {

        public void processInboundMessage(final ByteMessageInput input) {
        }

        public HttpMessageWriter waitForOutgoingHttpMessage(final int millis) {
            synchronized (outboundQueue) {
                if (outboundQueue.element() != null) {
                    while (! outboundQueue.isEmpty()) {
                        Action action = outboundQueue.remove();
                    }
                }
            }
            return null;
        }
    }
}
