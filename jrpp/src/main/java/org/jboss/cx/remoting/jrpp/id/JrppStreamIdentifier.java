package org.jboss.cx.remoting.jrpp.id;

import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class JrppStreamIdentifier extends JrppSubChannelIdentifier implements StreamIdentifier {

    private static final long serialVersionUID = 1L;

    public JrppStreamIdentifier() {
    }

    public JrppStreamIdentifier(final boolean client, final int id) {
        super(client, id);
    }

    public boolean equals(Object obj) {
        return obj instanceof JrppStreamIdentifier && super.equals(obj);
    }
}
