package org.jboss.cx.remoting.jrpp.id;

import java.io.IOException;
import java.io.ObjectInputStream;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.MessageInput;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class JrppServiceIdentifier extends JrppSubChannelIdentifier implements ServiceIdentifier {
    public JrppServiceIdentifier(short id) throws IOException {
        super(id);
    }

    public JrppServiceIdentifier(MessageInput input) throws IOException {
        super(input);
    }

    public boolean equals(Object obj) {
        return obj instanceof JrppServiceIdentifier && super.equals(obj);
    }
}
