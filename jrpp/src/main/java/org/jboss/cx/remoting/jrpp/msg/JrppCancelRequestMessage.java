package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppRequestIdentifier;

/**
 *
 */
public final class JrppCancelRequestMessage extends JrppRequestMessage {
    private final boolean mayInterrupt;

    public JrppCancelRequestMessage(final JrppContextIdentifier contextIdentifier, final JrppRequestIdentifier requestIdentifier, final boolean mayInterrupt) {
        super(contextIdentifier, requestIdentifier);
        this.mayInterrupt = mayInterrupt;
    }

    public JrppCancelRequestMessage(ObjectInputStream ois) throws IOException {
        super(ois);
        mayInterrupt = ois.readBoolean();
    }

    public boolean isMayInterrupt() {
        return mayInterrupt;
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
