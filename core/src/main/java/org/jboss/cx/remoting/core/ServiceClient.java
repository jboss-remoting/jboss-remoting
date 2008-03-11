package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public interface ServiceClient {
    void handleClosing() throws RemotingException;
}
