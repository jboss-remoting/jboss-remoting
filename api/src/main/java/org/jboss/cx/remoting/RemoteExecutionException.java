package org.jboss.cx.remoting;

import java.util.concurrent.ExecutionException;

/**
 *
 */
public class RemoteExecutionException extends ExecutionException {

    public RemoteExecutionException() {
    }

    public RemoteExecutionException(String msg) {
        super(msg);
    }

    public RemoteExecutionException(Throwable cause) {
        super(cause);
    }

    public RemoteExecutionException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public final void throwRuntime() throws RuntimeException {
        final Throwable cause = getCause();
        if (cause instanceof RuntimeException) {
            throw ((RuntimeException)cause);
        }
    }
}
