package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;

/**
 *
 */
public final class JrppCloseContextMessage extends JrppContextMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public JrppCloseContextMessage(final ContextIdentifier contextIdentifier) {
        super(contextIdentifier);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
