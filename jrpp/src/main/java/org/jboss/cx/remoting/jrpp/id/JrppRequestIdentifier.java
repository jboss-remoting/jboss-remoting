package org.jboss.cx.remoting.jrpp.id;

import java.io.IOException;
import org.jboss.cx.remoting.core.util.MessageInput;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class JrppRequestIdentifier extends JrppSubChannelIdentifier implements RequestIdentifier {
    public JrppRequestIdentifier(short id) throws IOException {
        super(id);
    }

    public JrppRequestIdentifier(MessageInput input) throws IOException {
        super(input);
    }

    public boolean equals(Object obj) {
        return obj instanceof JrppRequestIdentifier && super.equals(obj);
    }
}
