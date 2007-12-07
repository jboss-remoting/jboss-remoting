package org.jboss.cx.remoting.jrpp.msg;

import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.Header;
import org.jboss.cx.remoting.jrpp.id.JrppRequestIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;
import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;

/**
 *
 */
public final class JrppRequest extends JrppRequestBodyMessage {

    public JrppRequest(final JrppContextIdentifier contextIdentifier, final JrppRequestIdentifier requestIdentifier, final Object body, final Header[] headers) {
        super(contextIdentifier, requestIdentifier, body, headers);
    }

    public JrppRequest(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        super(ois);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
