package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 *
 */
public final class JrppCancelAcknowledgeMessage extends JrppRequestMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public JrppCancelAcknowledgeMessage(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier) {
        super(contextIdentifier, requestIdentifier);
    }

    protected JrppCancelAcknowledgeMessage(ObjectInputStream ois) throws IOException {
        super(ois);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
