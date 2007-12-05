package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.ServiceLocator;

/**
 *
 */
public final class JrppServiceRequestMessage extends JrppServiceMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final ServiceLocator<?, ?> serviceLocator;

    public JrppServiceRequestMessage(final ServiceIdentifier serviceIdentifier, final ServiceLocator<?, ?> serviceLocator) {
        super(serviceIdentifier);
        this.serviceLocator = serviceLocator;
    }

    public ServiceLocator<?, ?> getServiceLocator() {
        return serviceLocator;
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
