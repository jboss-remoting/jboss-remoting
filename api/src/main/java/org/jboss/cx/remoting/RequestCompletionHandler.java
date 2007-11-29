package org.jboss.cx.remoting;

/**
 *
 */
public interface RequestCompletionHandler {
    void notifyComplete(FutureReply futureReply);
}
