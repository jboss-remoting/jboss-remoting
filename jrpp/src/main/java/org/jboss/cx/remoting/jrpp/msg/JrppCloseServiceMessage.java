package org.jboss.cx.remoting.jrpp.msg;

import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;

/**
 *
 */
public final class JrppCloseServiceMessage extends JrppServiceMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public JrppCloseServiceMessage(final ServiceIdentifier serviceIdentifier) {
        super(serviceIdentifier);
    }

    protected JrppCloseServiceMessage(ObjectInputStream ois) throws IOException {
        super(ois);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
