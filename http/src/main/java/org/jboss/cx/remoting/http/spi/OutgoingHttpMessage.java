package org.jboss.cx.remoting.http.spi;

import java.io.IOException;
import org.jboss.cx.remoting.spi.ByteMessageOutput;

/**
 *
 */
public interface OutgoingHttpMessage extends HttpMessage {
    void writeMessageData(ByteMessageOutput byteOutput) throws IOException;
}
