package org.jboss.cx.remoting.spi;

import org.jboss.cx.remoting.RemoteExecutionException;

/**
 *
 */
public interface InterceptorContext {
    void nextRequest(Object request);

    void nextReply(Object reply);

    void nextException(RemoteExecutionException exception);

    void nextCancelRequest(boolean mayInterrupt);

    void nextCancelAcknowledge();
}
