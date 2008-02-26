package org.jboss.cx.remoting;

import java.io.IOException;

/**
 *
 */
public class RemotingException extends IOException {
    public RemotingException() {
    }

    public RemotingException(String s) {
        super(s);
    }

    public RemotingException(String s, Throwable cause) {
        super(getCauseString(s, cause));
        setStackTrace(cause.getStackTrace());
    }

    public RemoteExecutionException getExecutionException() {
        final RemoteExecutionException ex = new RemoteExecutionException(getMessage(), this);
        ex.setStackTrace(getStackTrace());
        return ex;
    }

    private static String getCauseString(String ourMsg, Throwable cause) {
        final String message = cause.getMessage();
        final String className = cause.getClass().getName();
        final StringBuilder builder = new StringBuilder(40);
        builder.append(ourMsg);
        builder.append(" (Caused by an exception of type ");
        builder.append(className);
        if (message != null) {
            builder.append(" with message: ");
            builder.append(message);
        }
        builder.append(")");
        return builder.toString();
    }
}
