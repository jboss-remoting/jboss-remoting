package org.jboss.cx.remoting.http.spi;

/**
 *
 */
public interface RemotingHttpSessionContext {
    void processInboundMessage(IncomingHttpMessage incomingHttpMessage);

    OutgoingHttpMessage getOutgoingHttpMessage();

    OutgoingHttpMessage waitForOutgoingHttpMessage(long millis);
}
