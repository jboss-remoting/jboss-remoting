package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;

/**
 *
 */
public final class JrppServiceActivateMessage extends JrppServiceMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public JrppServiceActivateMessage(final ServiceIdentifier serviceIdentifier) {
        super(serviceIdentifier);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
