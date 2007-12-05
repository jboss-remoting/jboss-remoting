package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;

/**
 *
 */
public abstract class JrppServiceMessage extends JrppMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    protected final ServiceIdentifier serviceIdentifier;

    protected JrppServiceMessage(final ServiceIdentifier serviceIdentifier) {
        this.serviceIdentifier = serviceIdentifier;
    }

    public ServiceIdentifier getServiceIdentifier() {
        return serviceIdentifier;
    }
}
