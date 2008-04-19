package org.jboss.cx.remoting.http.spi;

/**
 *
 */
public interface RemotingHttpChannelContext {
    /**
     * Process an HTTP message that has arrived.
     *
     * @param incomingHttpMessage the HTTP message
     */
    void processInboundMessage(IncomingHttpMessage incomingHttpMessage);

    /**
     * Wait for an outgoing HTTP message to become available, up to a certain time limit.  If no message is available
     * within the specified time limit, or if the thread is interrupted before a message could become available, return
     * an empty message.
     *
     * @param millis the amount of time to wait in millseconds, {@code 0} to not wait, or {@code -1} to wait indefinitely.
     * @return an outgoing HTTP message
     */
    OutgoingHttpMessage waitForOutgoingHttpMessage(int millis);
}
