package org.jboss.cx.remoting.jrpp.id;

import java.io.IOException;
import java.io.ObjectInputStream;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class JrppContextIdentifier extends JrppSubChannelIdentifier implements ContextIdentifier {
    public JrppContextIdentifier(short id) throws IOException {
        super(id);
    }

    public JrppContextIdentifier(ObjectInputStream ois) throws IOException {
        super(ois);
    }

    public boolean equals(Object obj) {
        return obj instanceof JrppContextIdentifier && super.equals(obj);
    }
}
