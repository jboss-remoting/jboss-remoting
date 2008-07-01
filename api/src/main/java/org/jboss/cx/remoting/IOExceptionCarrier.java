package org.jboss.cx.remoting;

import java.io.IOException;

/**
 * A runtime exception that carries an {@link java.io.IOException} as a cause.
 */
public class IOExceptionCarrier extends RuntimeException {

    private static final long serialVersionUID = -1602940590696531671L;

    /**
     * Construct a new carrier.
     *
     * @param cause the nested cause
     */
    public IOExceptionCarrier(IOException cause) {
        super(cause);
    }

    /**
     * Get the cause.
     *
     * @return the cause
     */
    public IOException getCause() {
        return (IOException) super.getCause();
    }
}
