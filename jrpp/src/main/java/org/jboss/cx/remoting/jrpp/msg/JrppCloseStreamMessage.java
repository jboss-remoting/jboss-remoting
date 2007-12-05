package org.jboss.cx.remoting.jrpp.msg;

import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import java.io.Serializable;

/**
 *
 */
public final class JrppCloseStreamMessage extends JrppStreamMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public JrppCloseStreamMessage(final ContextIdentifier contextIdentifier, final StreamIdentifier streamIdentifier) {
        super(contextIdentifier, streamIdentifier);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
