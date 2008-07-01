package org.jboss.cx.remoting.core;

import java.io.Serializable;

/**
 *
 */
public final class ClientMarker implements Serializable {
    private static final long serialVersionUID = 1L;

    private ClientIdentifier clientIdentifer;

    public ClientMarker() {
    }

    public ClientMarker(final ClientIdentifier clientIdentifer) {
        this.clientIdentifer = clientIdentifer;
    }

    public ClientIdentifier getClientIdentifer() {
        return clientIdentifer;
    }

    public void setContextIdentifer(final ClientIdentifier clientIdentifer) {
        this.clientIdentifer = clientIdentifer;
    }
}
