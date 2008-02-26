package org.jboss.cx.remoting.jrpp.id;

import java.io.IOException;
import org.jboss.cx.remoting.util.MessageInput;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class JrppServiceIdentifier extends JrppSubChannelIdentifier implements ServiceIdentifier {
    public JrppServiceIdentifier() {
    }

    public JrppServiceIdentifier(final boolean client, final int id) {
        super(client, id);
    }

    public boolean equals(Object obj) {
        return obj instanceof JrppServiceIdentifier && super.equals(obj);
    }
}
