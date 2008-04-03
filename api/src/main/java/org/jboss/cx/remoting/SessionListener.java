package org.jboss.cx.remoting;

/**
 *
 */
public interface SessionListener {
    void handleSessionOpened(Session session);

    void handleSessionClosed(Session session);
}
