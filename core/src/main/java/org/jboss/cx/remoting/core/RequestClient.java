package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RemoteExecutionException;

/**
 *
 */
public interface RequestClient<O> {
    // Outbound protocol messages

    void handleReply(final O reply) throws RemotingException;

    void handleException(final RemoteExecutionException cause) throws RemotingException;

    void handleCancelAcknowledge() throws RemotingException;
}
