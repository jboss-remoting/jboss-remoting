package org.jboss.cx.remoting.http;

import org.jboss.cx.remoting.util.ByteMessageInput;

/**
 *
 */
public interface RemotingHttpServerContext {
    RemotingHttpChannelContext processUnsolicitedInboundMessage(ByteMessageInput messageInput);
}
