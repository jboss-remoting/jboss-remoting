package org.jboss.cx.remoting.jrpp.id;

import java.io.IOException;
import java.io.ObjectInputStream;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.MessageInput;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class JrppRequestIdentifier extends JrppSubChannelIdentifier implements RequestIdentifier {
    public JrppRequestIdentifier(short id) throws IOException {
        super(id);
    }

    public JrppRequestIdentifier(MessageInput input) throws IOException {
        super(input);
    }

    public boolean equals(Object obj) {
        return obj instanceof JrppRequestIdentifier && super.equals(obj);
    }
}
