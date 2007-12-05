package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;

/**
 *
 */
public abstract class JrppContextMessage extends JrppMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final ContextIdentifier contextIdentifier;

    protected JrppContextMessage(final ContextIdentifier contextIdentifier) {
        this.contextIdentifier = contextIdentifier;
    }

    public ContextIdentifier getContextIdentifier() {
        return contextIdentifier;
    }
}
