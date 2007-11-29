package org.jboss.cx.remoting.spi.protocol;

import org.jboss.cx.remoting.spi.Registration;

/**
 *
 */
public interface ProtocolRegistration extends Registration {
    ProtocolServerContext getProtocolServerContext();
}
