package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemotingException;
import java.util.concurrent.Executor;

/**
 *
 */
public interface RequestServer<I> {

    // Outbound protocol messages

    void handleRequest(I request, final Executor streamExecutor) throws RemotingException;

    void handleCancelRequest(boolean mayInterrupt) throws RemotingException;
}
