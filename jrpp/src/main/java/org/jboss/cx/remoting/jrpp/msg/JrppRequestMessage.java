package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppRequestIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;

/**
 *
 */
public abstract class JrppRequestMessage extends JrppContextMessage {

    protected JrppRequestIdentifier requestIdentifier;

    protected JrppRequestMessage(final JrppContextIdentifier contextIdentifier, final JrppRequestIdentifier requestIdentifier) {
        super(contextIdentifier);
        this.requestIdentifier = requestIdentifier;
    }

    protected JrppRequestMessage(ObjectInputStream ois) throws IOException {
        super(ois);
        requestIdentifier = new JrppRequestIdentifier(ois);
    }

    public final JrppRequestIdentifier getRequestIdentifier() {
        return requestIdentifier;
    }
}
