package org.jboss.cx.remoting.core.util;

/**
 *
 */
public final class Base64DecodingException extends Exception {
    public Base64DecodingException() {
    }

    public Base64DecodingException(String message) {
        super(message);
    }

    public Base64DecodingException(String message, Throwable cause) {
        super(message, cause);
    }

    public Base64DecodingException(Throwable cause) {
        super(cause);
    }
}
