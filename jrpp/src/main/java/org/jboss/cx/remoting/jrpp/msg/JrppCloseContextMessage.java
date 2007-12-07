package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;

/**
 *
 */
public final class JrppCloseContextMessage extends JrppContextMessage {

    public JrppCloseContextMessage(final JrppContextIdentifier contextIdentifier) {
        super(contextIdentifier);
    }

    public JrppCloseContextMessage(ObjectInputStream ois) throws IOException {
        super(ois);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
