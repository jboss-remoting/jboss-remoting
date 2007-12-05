package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 *
 */
public abstract class JrppRequestMessage extends JrppContextMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    protected RequestIdentifier requestIdentifier;

    protected JrppRequestMessage(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier) {
        super(contextIdentifier);
        this.requestIdentifier = requestIdentifier;
    }

    public RequestIdentifier getRequestIdentifier() {
        return requestIdentifier;
    }
}
