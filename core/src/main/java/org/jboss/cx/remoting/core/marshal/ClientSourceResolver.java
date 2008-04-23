package org.jboss.cx.remoting.core.marshal;

import java.io.IOException;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;

/**
 *
 */
public final class ClientSourceResolver implements ObjectResolver {

    private static final long serialVersionUID = 7850552704308592325L;

    public Object writeReplace(final Object original) throws IOException {
        return null;
    }

    public Object readResolve(final Object original) throws IOException {

        return null;
    }
}