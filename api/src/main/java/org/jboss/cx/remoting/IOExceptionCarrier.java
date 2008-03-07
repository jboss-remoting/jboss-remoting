package org.jboss.cx.remoting;

import java.io.IOException;

/**
 *
 */
public class IOExceptionCarrier extends RuntimeException {
    public IOExceptionCarrier(IOException cause) {
        super(cause);
    }

    public IOException getCause() {
        return (IOException) super.getCause();
    }
}
