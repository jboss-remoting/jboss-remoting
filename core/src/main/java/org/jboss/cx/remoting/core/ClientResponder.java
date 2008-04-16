package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public interface ClientResponder<I, O> {
    RequestResponder<I> createNewRequest(RequestInitiator<O> requestInitiator) throws RemotingException;

    void handleClose(boolean immediate, boolean cancel) throws RemotingException;
}
