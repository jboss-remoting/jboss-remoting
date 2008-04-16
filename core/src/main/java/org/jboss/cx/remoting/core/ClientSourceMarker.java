package org.jboss.cx.remoting.core;

import java.io.Serializable;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;

/**
 *
 */
public final class ClientSourceMarker implements Serializable {
    private static final long serialVersionUID = 1L;

    private ServiceIdentifier serviceIdentifier;

    public ClientSourceMarker() {
    }

    public ClientSourceMarker(final ServiceIdentifier serviceIdentifier) {
        this.serviceIdentifier = serviceIdentifier;
    }

    public ServiceIdentifier getServiceIdentifier() {
        return serviceIdentifier;
    }

    public void setServiceIdentifier(final ServiceIdentifier serviceIdentifier) {
        this.serviceIdentifier = serviceIdentifier;
    }
}