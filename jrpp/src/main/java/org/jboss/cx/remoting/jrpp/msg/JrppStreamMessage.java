package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppStreamIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;

/**
 *
 */
public abstract class JrppStreamMessage extends JrppContextMessage {

    private final JrppStreamIdentifier streamIdentifier;

    protected JrppStreamMessage(final JrppContextIdentifier contextIdentifier, final JrppStreamIdentifier streamIdentifier) {
        super(contextIdentifier);
        this.streamIdentifier = streamIdentifier;
    }

    protected JrppStreamMessage(ObjectInputStream ois) throws IOException {
        super(ois);
        streamIdentifier = new JrppStreamIdentifier(ois);
    }

    public final JrppStreamIdentifier getStreamIdentifier() {
        return streamIdentifier;
    }
}
