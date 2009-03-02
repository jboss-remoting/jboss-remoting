package org.jboss.remoting3;

import java.io.IOException;

/**
 * A general Remoting exception.  Used as a base class in order to provide constructors which accept any combination
 * of {@code cause} and {@code message}.
 */
public abstract class RemotingException extends IOException {

    private static final long serialVersionUID = 1540716301579397423L;

    /**
     * Constructs a <tt>RemotingException</tt> with no detail message. The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    protected RemotingException() {
    }

    /**
     * Constructs a <tt>RemotingException</tt> with the specified detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     */
    protected RemotingException(String msg) {
        super(msg);
    }

    /**
     * Constructs a <tt>RemotingException</tt> with the specified cause. The detail message is set to:
     * <pre>
     *  (cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of <tt>cause</tt>).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    protected RemotingException(Throwable cause) {
        initCause(cause);
    }

    /**
     * Constructs a <tt>RemotingException</tt> with the specified detail message and cause.
     *
     * @param msg the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    protected RemotingException(String msg, Throwable cause) {
        super(msg);
        initCause(cause);
    }
}
