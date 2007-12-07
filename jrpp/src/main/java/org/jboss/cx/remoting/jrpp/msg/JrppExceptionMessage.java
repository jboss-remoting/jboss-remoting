package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.jrpp.id.JrppRequestIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;

/**
 *
 */
public final class JrppExceptionMessage extends JrppRequestMessage {
    private final RemoteExecutionException exception;

    public JrppExceptionMessage(final JrppContextIdentifier contextIdentifier, final JrppRequestIdentifier requestIdentifier, final RemoteExecutionException exception) {
        super(contextIdentifier, requestIdentifier);
        this.exception = exception;
    }

    public JrppExceptionMessage(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        super(ois);
        exception = (RemoteExecutionException) ois.readObject();
    }

    public RemoteExecutionException getException() {
        return exception;
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
