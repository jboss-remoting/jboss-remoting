package org.jboss.cx.remoting.http.spi;

import java.net.InetAddress;

/**
 *
 */
public interface IncomingHttpRequest extends HttpRequest {
    InetAddress getRemoteAddress();

    int getRemotePort();
}
