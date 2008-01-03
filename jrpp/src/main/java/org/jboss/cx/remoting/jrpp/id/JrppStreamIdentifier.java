package org.jboss.cx.remoting.jrpp.id;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectInput;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.spi.protocol.MessageInput;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class JrppStreamIdentifier extends JrppSubChannelIdentifier implements StreamIdentifier {
    public JrppStreamIdentifier(short id) throws IOException {
        super(id);
    }

    public JrppStreamIdentifier(ObjectInput input) throws IOException {
        super(input);
    }

    public boolean equals(Object obj) {
        return obj instanceof JrppStreamIdentifier && super.equals(obj);
    }
}
