package org.jboss.cx.remoting.http;

import java.io.IOException;
import org.jboss.cx.remoting.util.ByteMessageOutput;

/**
 *
 */
public interface HttpMessageWriter {
    void writeMessageData(ByteMessageOutput byteOutput) throws IOException;
}
