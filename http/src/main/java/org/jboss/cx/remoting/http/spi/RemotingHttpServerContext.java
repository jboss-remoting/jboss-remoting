package org.jboss.cx.remoting.http.spi;

/**
 *
 */
public interface RemotingHttpServerContext {
    RemotingHttpChannelContext processUnsolicitedInboundMessage(IncomingHttpMessage incomingHttpMessage);
}
