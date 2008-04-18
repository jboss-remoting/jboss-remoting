package org.jboss.cx.remoting.http.urlconnection;

/**
 *
 */
public final class HttpUrlChannel extends AbstractHttpUrlChannel {

    // lifecycle

    public void create() {
        final String protocol = getConnectUrl().getProtocol();
        if (! "http".equals(protocol)) {
            throw new IllegalArgumentException("Cannot use " + HttpUrlChannel.class.getName() + " for protocol \"" + protocol + "\"");
        }
    }
}
