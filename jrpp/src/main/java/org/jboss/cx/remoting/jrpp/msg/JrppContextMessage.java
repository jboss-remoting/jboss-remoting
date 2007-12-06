package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;
import org.jboss.cx.remoting.jrpp.WritableObject;

/**
 *
 */
public abstract class JrppContextMessage extends JrppMessage implements WritableObject {
    protected final JrppContextIdentifier contextIdentifier;

    protected JrppContextMessage(final JrppContextIdentifier contextIdentifier) {
        this.contextIdentifier = contextIdentifier;
    }

    protected JrppContextMessage(ObjectInputStream ois) throws IOException {
        super(ois);
        contextIdentifier = new JrppContextIdentifier(ois);
    }

    public void writeObjectData(ObjectOutputStream oos) throws IOException {
        super.writeObjectData(oos);
        contextIdentifier.writeObjectData(oos);
    }

    public final ContextIdentifier getContextIdentifier() {
        return contextIdentifier;
    }
}
