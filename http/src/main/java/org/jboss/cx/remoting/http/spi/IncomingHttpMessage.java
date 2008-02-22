package org.jboss.cx.remoting.http.spi;

import java.io.IOException;
import java.net.InetAddress;
import org.jboss.cx.remoting.util.ByteInput;

/**
 *
 */
public interface IncomingHttpMessage extends HttpMessage {
    ByteInput getMessageData() throws IOException;

    InetAddress getRemoteAddress();

    int getRemotePort();

    InetAddress getLocalAddress();

    int getLocalPort();
}
