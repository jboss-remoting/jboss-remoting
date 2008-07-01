package org.jboss.cx.remoting;

/**
 * A session listener.  Called when a session is opened on an endpoint.
 */
public interface SessionListener {

    /**
     * Receive notification that the session was opened.
     *
     * @param session the new session
     */
    void handleSessionOpened(Session session);
}
