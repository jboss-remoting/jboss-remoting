package org.jboss.cx.remoting.core;

import java.io.Serializable;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;

/**
 *
 */
public final class ContextMarker implements Serializable {
    private static final long serialVersionUID = 1L;

    private ContextIdentifier contextIdentifer;

    public ContextMarker() {
    }

    public ContextMarker(final ContextIdentifier contextIdentifer) {
        this.contextIdentifer = contextIdentifer;
    }

    public ContextIdentifier getContextIdentifer() {
        return contextIdentifer;
    }

    public void setContextIdentifer(final ContextIdentifier contextIdentifer) {
        this.contextIdentifer = contextIdentifer;
    }
}
