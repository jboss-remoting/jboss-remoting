package org.jboss.cx.remoting.http.spi;

import java.io.IOException;
import org.jboss.cx.remoting.core.util.ByteOutput;

/**
 *
 */
public interface OutgoingHttpMessage extends HttpMessage {
    void writeMessageData(ByteOutput byteOutput) throws IOException;
}
