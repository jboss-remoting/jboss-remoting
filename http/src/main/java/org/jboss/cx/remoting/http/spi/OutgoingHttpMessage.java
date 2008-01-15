package org.jboss.cx.remoting.http.spi;

import java.io.IOException;
import java.io.OutputStream;

/**
 *
 */
public interface OutgoingHttpMessage {
    void writeTo(OutputStream outputStream) throws IOException;
}
