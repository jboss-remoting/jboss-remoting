package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;

/**
 *
 */
public final class JrppCloseRequestMessage extends JrppRequestMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public JrppCloseRequestMessage(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier) {
        super(contextIdentifier, requestIdentifier);
    }

    protected JrppCloseRequestMessage(ObjectInputStream ois) throws IOException {
        super(ois);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
