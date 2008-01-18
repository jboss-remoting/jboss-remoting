package org.jboss.cx.remoting.http.spi;

/**
 *
 */
public interface ReadyNotifier {
    void notifyReady(RemotingHttpSessionContext context);
}
