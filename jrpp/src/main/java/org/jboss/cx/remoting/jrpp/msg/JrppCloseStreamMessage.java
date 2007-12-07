package org.jboss.cx.remoting.jrpp.msg;

import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppStreamIdentifier;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;

/**
 *
 */
public final class JrppCloseStreamMessage extends JrppStreamMessage {

    public JrppCloseStreamMessage(final JrppContextIdentifier contextIdentifier, final JrppStreamIdentifier streamIdentifier) {
        super(contextIdentifier, streamIdentifier);
    }

    public JrppCloseStreamMessage(ObjectInputStream ois) throws IOException {
        super(ois);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
