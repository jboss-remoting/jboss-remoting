package org.jboss.cx.remoting.http.spi;

import java.io.IOException;
import java.net.InetAddress;
import org.jboss.cx.remoting.spi.ByteMessageInput;

/**
 *
 */
public interface IncomingHttpMessage extends HttpMessage {
    ByteMessageInput getMessageData() throws IOException;

    InetAddress getRemoteAddress();

    int getRemotePort();

    InetAddress getLocalAddress();

    int getLocalPort();
}
