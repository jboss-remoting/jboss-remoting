package org.jboss.cx.remoting;

/**
 *
 */
public interface RequestCompletionHandler<T> {
    void notifyComplete(FutureReply<T> futureReply);
}
