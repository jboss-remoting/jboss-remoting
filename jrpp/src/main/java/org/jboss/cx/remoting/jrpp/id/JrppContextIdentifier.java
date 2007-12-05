package org.jboss.cx.remoting.jrpp.id;

import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import java.io.IOException;

/**
 *
 */
public final class JrppContextIdentifier extends JrppSubChannelIdentifier implements ContextIdentifier {
    public JrppContextIdentifier(final IdentifierManager manager) throws IOException {
        super(manager);
    }
}
