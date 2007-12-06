package org.jboss.cx.remoting.jrpp.id;

import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class JrppStreamIdentifier extends JrppSubChannelIdentifier implements StreamIdentifier {
    public JrppStreamIdentifier(short id) throws IOException {
        super(id);
    }

    public JrppStreamIdentifier(ObjectInputStream ois) throws IOException {
        super(ois);
    }

    public boolean equals(Object obj) {
        return obj instanceof JrppStreamIdentifier && super.equals(obj);
    }
}
