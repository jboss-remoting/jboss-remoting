package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public interface ClientInitiator {
    void handleClosing(boolean done) throws RemotingException;
}
