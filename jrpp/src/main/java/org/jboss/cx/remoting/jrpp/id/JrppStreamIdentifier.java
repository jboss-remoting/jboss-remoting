package org.jboss.cx.remoting.jrpp.id;

import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import java.io.IOException;

/**
 *
 */
public final class JrppStreamIdentifier extends JrppSubChannelIdentifier implements StreamIdentifier {
    public JrppStreamIdentifier(final IdentifierManager manager) throws IOException {
        super(manager);
    }
}
