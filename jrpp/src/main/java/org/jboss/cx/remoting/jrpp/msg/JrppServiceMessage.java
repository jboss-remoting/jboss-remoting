package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppServiceIdentifier;

/**
 *
 */
public abstract class JrppServiceMessage extends JrppMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final ServiceIdentifier serviceIdentifier;

    protected JrppServiceMessage(final ServiceIdentifier serviceIdentifier) {
        this.serviceIdentifier = serviceIdentifier;
    }

    protected JrppServiceMessage(ObjectInputStream ois) throws IOException {
        super(ois);
        serviceIdentifier = JrppServiceIdentifier.forValue(ois.readShort());
    }

    public final ServiceIdentifier getServiceIdentifier() {
        return serviceIdentifier;
    }
}
