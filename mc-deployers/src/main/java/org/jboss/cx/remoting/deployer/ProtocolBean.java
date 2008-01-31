package org.jboss.cx.remoting.deployer;

import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;

/**
 *
 */
public final class ProtocolBean {
    private Endpoint endpoint;
    private String scheme;
    private ProtocolRegistrationSpec registrationSpec;
    private ProtocolRegistration registration;

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void create() throws RemotingException {
        registrationSpec = ProtocolRegistrationSpec.DEFAULT
                .setScheme(scheme)
                .setProtocolHandlerFactory(null);
        registration = endpoint.registerProtocol(registrationSpec);
    }

    public void start() {
        registration.start();
    }

    public void stop() {
        registration.stop();
    }

    public void destroy() {
        registration.unregister();
    }
}
