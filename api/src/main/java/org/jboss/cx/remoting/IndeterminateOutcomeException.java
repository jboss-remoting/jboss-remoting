package org.jboss.cx.remoting;

/**
 *
 */
public class IndeterminateOutcomeException extends RemoteExecutionException {
    public IndeterminateOutcomeException() {
    }

    public IndeterminateOutcomeException(String msg) {
        super(msg);
    }

    public IndeterminateOutcomeException(Throwable cause) {
        super(cause);
    }

    public IndeterminateOutcomeException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
