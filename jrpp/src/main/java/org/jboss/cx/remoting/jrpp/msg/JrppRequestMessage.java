package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppRequestIdentifier;

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

    protected JrppRequestMessage(ObjectInputStream ois) throws IOException {
        super(ois);
        requestIdentifier = JrppRequestIdentifier.forValue(ois.readShort());
    }

    public final RequestIdentifier getRequestIdentifier() {
        return requestIdentifier;
    }
}
