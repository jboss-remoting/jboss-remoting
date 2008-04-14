package org.jboss.cx.remoting.http.spi;

/**
 *
 */
public interface HttpChannel {
    void setChannelContext(RemotingHttpChannelContext channelContext);

    void sendMessage(OutgoingHttpMessage message);

    void close();
}
