package org.jboss.cx.remoting;

/**
 *
 */
public class UnknownIdentifierException extends RemotingException {
    public UnknownIdentifierException() {
    }

    public UnknownIdentifierException(String msg) {
        super(msg);
    }

    public UnknownIdentifierException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
