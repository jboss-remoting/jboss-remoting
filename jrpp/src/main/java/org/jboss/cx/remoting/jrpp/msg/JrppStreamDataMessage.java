package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;

/**
 *
 */
public final class JrppStreamDataMessage extends JrppStreamMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Object data;

    public JrppStreamDataMessage(final ContextIdentifier contextIdentifier, final StreamIdentifier streamIdentifier, final Object data) {
        super(contextIdentifier, streamIdentifier);
        this.data = data;
    }

    public JrppStreamDataMessage(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        super(ois);
        data = ois.readObject();
    }

    public Object getData() {
        return data;
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
