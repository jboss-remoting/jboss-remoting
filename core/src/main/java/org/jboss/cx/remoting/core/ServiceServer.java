package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public interface ServiceServer<I, O> {
    void handleClose() throws RemotingException;

    ContextServer<I, O> createNewContext(ContextClient client) throws RemotingException;
}
