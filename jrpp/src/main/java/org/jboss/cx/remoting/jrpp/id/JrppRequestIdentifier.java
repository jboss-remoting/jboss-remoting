package org.jboss.cx.remoting.jrpp.id;

import java.io.IOException;
import org.jboss.cx.remoting.util.MessageInput;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class JrppRequestIdentifier extends JrppSubChannelIdentifier implements RequestIdentifier {
    public JrppRequestIdentifier() {
    }

    public JrppRequestIdentifier(final boolean client, final int id) {
        super(client, id);
    }

    public boolean equals(Object obj) {
        return obj instanceof JrppRequestIdentifier && super.equals(obj);
    }
}
