package org.jboss.cx.remoting.http.spi;

/**
 *
 */
public interface RemotingHttpChannelContext {

    void receiveMessage(IncomingHttpMessage incomingHttpMessage);

    void sendComplete(OutgoingHttpMessage outgoingHttpMessage);
}
