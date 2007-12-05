package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.RemoteExecutionException;

/**
 *
 */
public final class JrppExceptionMessage extends JrppRequestMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private final RemoteExecutionException exception;

    public JrppExceptionMessage(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) {
        super(contextIdentifier, requestIdentifier);
        this.exception = exception;
    }

    public RemoteExecutionException getException() {
        return exception;
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
