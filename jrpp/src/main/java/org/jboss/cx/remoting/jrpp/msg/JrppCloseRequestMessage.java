package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppRequestIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;

/**
 *
 */
public final class JrppCloseRequestMessage extends JrppRequestMessage {

    public JrppCloseRequestMessage(final JrppContextIdentifier contextIdentifier, final JrppRequestIdentifier requestIdentifier) {
        super(contextIdentifier, requestIdentifier);
    }

    public JrppCloseRequestMessage(ObjectInputStream ois) throws IOException {
        super(ois);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
