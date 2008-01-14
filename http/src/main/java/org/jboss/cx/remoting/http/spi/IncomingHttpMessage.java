package org.jboss.cx.remoting.http.spi;

import java.io.InputStream;
import java.net.InetAddress;

/**
 *
 */
public interface IncomingHttpMessage {
    InputStream getMessageData();

    InetAddress getLocalAddress();

    int getLocalPort();
}
