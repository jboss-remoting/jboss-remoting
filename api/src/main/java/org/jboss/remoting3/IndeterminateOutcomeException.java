package org.jboss.remoting3;

/**
 * An exception that is thrown when an operation terminates in such a way that the outcome cannot be known.
 */
public class IndeterminateOutcomeException extends RemotingException {

    private static final long serialVersionUID = 6304843915977033800L;

    /**
     * Constructs a <tt>IndeterminateOutcomeException</tt> with no detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     */
    public IndeterminateOutcomeException() {
    }

    /**
     * Constructs a <tt>IndeterminateOutcomeException</tt> with the specified detail message. The cause is not initialized,
     * and may subsequently be initialized by a call to {@link #initCause(Throwable) initCause}.
     *
     * @param msg the detail message
     */
    public IndeterminateOutcomeException(String msg) {
        super(msg);
    }

    /**
     * Constructs a <tt>IndeterminateOutcomeException</tt> with the specified cause. The detail message is set to:
     * <pre>
     *  (cause == null ? null : cause.toString())</pre>
     * (which typically contains the class and detail message of <tt>cause</tt>).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public IndeterminateOutcomeException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a <tt>IndeterminateOutcomeException</tt> with the specified detail message and cause.
     *
     * @param msg the detail message
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
     */
    public IndeterminateOutcomeException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
