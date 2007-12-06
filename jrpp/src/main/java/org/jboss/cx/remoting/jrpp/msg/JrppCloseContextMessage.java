package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;

/**
 *
 */
public final class JrppCloseContextMessage extends JrppContextMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public JrppCloseContextMessage(final ContextIdentifier contextIdentifier) {
        super(contextIdentifier);
    }

    protected JrppCloseContextMessage(ObjectInputStream ois) throws IOException {
        super(ois);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
