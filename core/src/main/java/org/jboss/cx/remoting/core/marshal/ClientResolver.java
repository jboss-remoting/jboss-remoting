package org.jboss.cx.remoting.core.marshal;

import java.io.IOException;
import org.jboss.cx.remoting.core.AbstractRealClient;
import org.jboss.cx.remoting.core.ClientMarker;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.cx.remoting.spi.protocol.ClientIdentifier;

/**
 *
 */
public final class ClientResolver implements ObjectResolver {

    private static final long serialVersionUID = 7850552704308592325L;

    public Object writeReplace(final Object original) throws IOException {
        if (original instanceof AbstractRealClient) {
            AbstractRealClient client = (AbstractRealClient) original;

            return null;
        } else {
            return original;
        }
    }

    public Object readResolve(final Object original) throws IOException {
        if (original instanceof ClientMarker) {
            ClientMarker clientMarker = (ClientMarker) original;
            ClientIdentifier clientIdentifier = clientMarker.getClientIdentifer();

            return null;
        } else {
            return original;
        }
    }
}
