package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.Header;
import org.jboss.cx.remoting.jrpp.mina.JrppProtocolDecoder;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppRequestIdentifier;

/**
 *
 */
public final class JrppReply extends JrppRequestBodyMessage {

    public JrppReply(final JrppContextIdentifier contextIdentifier, final JrppRequestIdentifier requestIdentifier, final Object body, final Header[] headers) {
        super(contextIdentifier, requestIdentifier, body, headers);
    }

    public JrppReply(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        super(ois);
    }

    public void accept(JrppMessageVisitor visitor) {
        visitor.visit(this);
    }
}
