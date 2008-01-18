package org.jboss.cx.remoting.http.spi;

import java.io.IOException;
import java.io.OutputStream;

/**
 *
 */
public interface OutgoingHttpMessage extends HttpMessage {
    void writeMessageData(OutputStream outputStream) throws IOException;
}
