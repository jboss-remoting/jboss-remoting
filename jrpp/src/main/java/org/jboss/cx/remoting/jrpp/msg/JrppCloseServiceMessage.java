package org.jboss.cx.remoting.jrpp.msg;

import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import java.io.Serializable;

/**
 *
 */
public final class JrppCloseServiceMessage extends JrppServiceMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public JrppCloseServiceMessage(final ServiceIdentifier serviceIdentifier) {
        super(serviceIdentifier);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
