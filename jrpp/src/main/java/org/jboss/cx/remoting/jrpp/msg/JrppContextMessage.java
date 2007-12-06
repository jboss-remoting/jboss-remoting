package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;

/**
 *
 */
public abstract class JrppContextMessage extends JrppMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final ContextIdentifier contextIdentifier;

    protected JrppContextMessage(final ContextIdentifier contextIdentifier) {
        this.contextIdentifier = contextIdentifier;
    }

    protected JrppContextMessage(ObjectInputStream ois) throws IOException {
        super(ois);
        contextIdentifier = new JrppContextIdentifier(ois);
    }

    public final ContextIdentifier getContextIdentifier() {
        return contextIdentifier;
    }
}
