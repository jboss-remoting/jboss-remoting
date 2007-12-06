package org.jboss.cx.remoting.jrpp.id;

import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class JrppRequestIdentifier extends JrppSubChannelIdentifier implements RequestIdentifier {
    public JrppRequestIdentifier(short id) throws IOException {
        super(id);
    }

    public JrppRequestIdentifier(ObjectInputStream ois) throws IOException {
        super(ois);
    }

    public boolean equals(Object obj) {
        return obj instanceof JrppRequestIdentifier && super.equals(obj);
    }
}
