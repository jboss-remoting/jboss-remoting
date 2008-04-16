package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public interface ServiceResponder<I, O> {
    void handleClose() throws RemotingException;

    ClientResponder<I, O> createNewClient(ClientInitiator clientInitiator) throws RemotingException;
}
