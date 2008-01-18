package org.jboss.cx.remoting.http.spi;

import java.net.URI;

/**
 *
 */
public interface HttpTransporter {
    void esablish(URI remoteUri, RemotingHttpSessionContext newSessionContext);
}
