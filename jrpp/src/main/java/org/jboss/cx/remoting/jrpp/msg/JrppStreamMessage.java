package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppStreamIdentifier;

/**
 *
 */
public abstract class JrppStreamMessage extends JrppContextMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final StreamIdentifier streamIdentifier;

    protected JrppStreamMessage(final ContextIdentifier contextIdentifier, final StreamIdentifier streamIdentifier) {
        super(contextIdentifier);
        this.streamIdentifier = streamIdentifier;
    }

    protected JrppStreamMessage(ObjectInputStream ois) throws IOException {
        super(ois);
        streamIdentifier = new JrppStreamIdentifier(ois);
    }

    public final StreamIdentifier getStreamIdentifier() {
        return streamIdentifier;
    }
}
