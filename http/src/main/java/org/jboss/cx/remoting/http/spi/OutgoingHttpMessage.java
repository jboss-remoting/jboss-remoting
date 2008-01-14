package org.jboss.cx.remoting.http.spi;

import java.io.OutputStream;
import java.io.IOException;

/**
 *
 */
public interface OutgoingHttpMessage {
    void writeTo(OutputStream outputStream) throws IOException;
}
