package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.Header;

/**
 *
 */
public abstract class JrppRequestBodyMessage extends JrppRequestMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Object body;
    private final Header[] headers;

    protected JrppRequestBodyMessage(final ContextIdentifier contextIdentifier, final RequestIdentifier requestIdentifier, final Object body, final Header[] headers) {
        super(contextIdentifier, requestIdentifier);
        this.body = body;
        this.headers = headers;
    }

    protected JrppRequestBodyMessage(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        super(ois);
        headers = (Header[]) ois.readObject();
        body = ois.readObject();
    }

    public final Object getBody() {
        return body;
    }

    public final Header[] getHeaders() {
        return headers;
    }
}
