package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public interface ContextServer<I, O> {
    RequestServer<I> createNewRequest(RequestClient<O> requestClient) throws RemotingException;

    void handleClose(boolean immediate, boolean cancel, boolean interrupt) throws RemotingException;
}
