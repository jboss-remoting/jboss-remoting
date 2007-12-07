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
public final class JrppCancelAcknowledgeMessage extends JrppRequestMessage {

    public JrppCancelAcknowledgeMessage(final JrppContextIdentifier contextIdentifier, final JrppRequestIdentifier requestIdentifier) {
        super(contextIdentifier, requestIdentifier);
    }

    public JrppCancelAcknowledgeMessage(ObjectInputStream ois) throws IOException {
        super(ois);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
