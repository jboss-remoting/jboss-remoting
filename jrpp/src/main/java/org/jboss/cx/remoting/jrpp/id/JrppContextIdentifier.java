package org.jboss.cx.remoting.jrpp.id;

import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class JrppContextIdentifier extends JrppSubChannelIdentifier implements ContextIdentifier {

    private static final long serialVersionUID = 1L;

    public JrppContextIdentifier() {
    }

    public JrppContextIdentifier(final boolean client, final int id) {
        super(client, id);
    }

    public boolean equals(Object obj) {
        return obj instanceof JrppContextIdentifier && super.equals(obj);
    }
}
