package org.jboss.cx.remoting.http.spi;

import java.io.InputStream;
import java.io.IOException;
import java.net.InetAddress;

/**
 *
 */
public interface IncomingHttpMessage extends HttpMessage {
    InputStream getMessageData() throws IOException;

    InetAddress getRemoteAddress();

    int getRemotePort();

    InetAddress getLocalAddress();

    int getLocalPort();
}
