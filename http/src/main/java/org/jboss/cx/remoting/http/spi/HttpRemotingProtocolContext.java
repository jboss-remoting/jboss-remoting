package org.jboss.cx.remoting.http.spi;

/**
 *
 */
public interface HttpRemotingProtocolContext {
    HttpRemotingSessionContext locateSession(IncomingHttpMessage message);
}
