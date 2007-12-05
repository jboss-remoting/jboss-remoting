package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;

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

    public StreamIdentifier getStreamIdentifier() {
        return streamIdentifier;
    }
}
