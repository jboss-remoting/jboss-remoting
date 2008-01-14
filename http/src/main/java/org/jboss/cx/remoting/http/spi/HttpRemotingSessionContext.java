package org.jboss.cx.remoting.http.spi;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public interface HttpRemotingSessionContext {
    void queueRequest(IncomingHttpRequest request);

    void queueReply(IncomingHttpReply reply);

    /**
     * Add a notifier to be called if there is data ready but there are no waiters for {@code getNextRequest}/{@code getNextReply}.
     * The notifier can use the {@code getNext*Immediate} methods to check for the next message.
     *
     * @param notifier the notifier
     */
    void addReadyNotifier(ReadyNotifier notifier);

    OutgoingHttpReply getNextReplyImmediate();

    OutgoingHttpRequest getNextRequestImmediate();

    OutgoingHttpReply getNextReply(long timeoutMillis) throws InterruptedException;

    OutgoingHttpRequest getNextRequest(long timeoutMillis) throws InterruptedException;

    /**
     * Get the callback handler to use to authenticate incoming HTTP messages.
     *
     * @return the callback handler
     */
    CallbackHandler getCallbackHandler();
}
