package org.jboss.remoting3.spi;

import java.io.IOException;

import org.wildfly.security.auth.server.SecurityDomain;
import org.xnio.OptionMap;
import org.xnio.channels.ConnectedStreamChannel;

/**
 * A provider interface that allows connections that have already been accepted to be converted to remoting
 * connections.
 *
 * @author Stuart Douglas
 */
public interface ExternalConnectionProvider {

    /**
     * Create a network server.
     *
     * @param optionMap              the server options
     * @param securityDomain the security domain to authenticate against
     * @return the channel adaptor
     * @throws java.io.IOException if the adaptor could not be created
     */
    ConnectionAdaptor createConnectionAdaptor(final OptionMap optionMap, SecurityDomain securityDomain) throws IOException;

    interface ConnectionAdaptor {

        void adapt(ConnectedStreamChannel channel);

    }
}
