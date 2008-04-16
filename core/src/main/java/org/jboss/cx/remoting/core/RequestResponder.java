package org.jboss.cx.remoting.core;

import java.util.concurrent.Executor;
import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public interface RequestResponder<I> {

    // Outbound protocol messages

    void handleRequest(I request, final Executor streamExecutor) throws RemotingException;

    void handleCancelRequest(boolean mayInterrupt) throws RemotingException;
}
