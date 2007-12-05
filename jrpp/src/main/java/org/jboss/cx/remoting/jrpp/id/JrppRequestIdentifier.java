package org.jboss.cx.remoting.jrpp.id;

import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import java.io.IOException;

/**
 *
 */
public final class JrppRequestIdentifier extends JrppSubChannelIdentifier implements RequestIdentifier {
    public JrppRequestIdentifier(final IdentifierManager manager) throws IOException {
        super(manager);
    }
}
