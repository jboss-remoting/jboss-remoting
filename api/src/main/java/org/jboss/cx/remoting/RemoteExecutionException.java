package org.jboss.cx.remoting;

/**
 * Exception thrown when execution of a remote operation fails for some reason.
 */
public class RemoteExecutionException extends RemotingException {

    private static final long serialVersionUID = 3580395686019440048L;

    /**
     * Constructs a <tt>RemoteExecutionException</tt> with no detail message.
     * The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public RemoteExecutionException() {
    }

    /**
     * Constructs a <tt>RemoteExecutionException</tt> with the specified detail
     * message. The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     */
    public RemoteExecutionException(String msg) {
        super(msg);
    }

    /**
     * Constructs a <tt>RemoteExecutionException</tt> with the specified cause.
     * The detail message is set to:
     * <pre>
     *  (cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of
     * <tt>cause</tt>).
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public RemoteExecutionException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a <tt>RemoteExecutionException</tt> with the specified detail
     * message and cause.
     *
     * @param  msg the detail message
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method)
     */
    public RemoteExecutionException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * Rethrow the cause, if it is a runtime exception.  This is a convenience method to extract a runtime exception
     * from a remote execution exception.
     *
     * @throws RuntimeException the cause
     */
    public final void throwRuntime() throws RuntimeException {
        final Throwable cause = getCause();
        if (cause instanceof RuntimeException) {
            throw ((RuntimeException)cause);
        }
    }
}
