package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public interface ContextClient {
    void handleClosing(boolean done) throws RemotingException;
}
