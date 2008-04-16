package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public interface ServiceInitiator {
    void handleClosing() throws RemotingException;
}
