package org.jboss.cx.remoting.jrpp.id;

import java.io.IOException;
import java.io.ObjectInputStream;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class JrppContextIdentifier extends JrppSubChannelIdentifier implements ContextIdentifier {
    public JrppContextIdentifier() {
    }

    public JrppContextIdentifier(final boolean client, final int id) {
        super(client, id);
    }

    public boolean equals(Object obj) {
        return obj instanceof JrppContextIdentifier && super.equals(obj);
    }
}
