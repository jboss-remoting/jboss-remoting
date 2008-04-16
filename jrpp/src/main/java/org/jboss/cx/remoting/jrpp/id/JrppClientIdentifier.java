package org.jboss.cx.remoting.jrpp.id;

import org.jboss.cx.remoting.spi.protocol.ClientIdentifier;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class JrppClientIdentifier extends JrppSubChannelIdentifier implements ClientIdentifier {

    private static final long serialVersionUID = 1L;

    public JrppClientIdentifier() {
    }

    public JrppClientIdentifier(final boolean client, final int id) {
        super(client, id);
    }

    public boolean equals(Object obj) {
        return obj instanceof JrppClientIdentifier && super.equals(obj);
    }
}
