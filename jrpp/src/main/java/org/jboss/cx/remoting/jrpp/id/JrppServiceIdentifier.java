package org.jboss.cx.remoting.jrpp.id;

import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import java.io.IOException;

/**
 *
 */
public final class JrppServiceIdentifier extends JrppSubChannelIdentifier implements ServiceIdentifier {
    public JrppServiceIdentifier(final IdentifierManager manager) throws IOException {
        super(manager);
    }
}
