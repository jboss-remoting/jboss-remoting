package org.jboss.cx.remoting.http.spi;

/**
 *
 */
public interface RemotingHttpServerContext {
    RemotingHttpSessionContext locateSession(IncomingHttpMessage message);
}
