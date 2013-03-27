package org.jboss.remoting3.spi;

import java.io.IOException;

import org.jboss.remoting3.security.ServerAuthenticationProvider;
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
     * @param authenticationProvider the authentication provider
     * @return the channel adaptor
     * @throws java.io.IOException if the adaptor could not be created
     */
    ConnectionAdaptor createConnectionAdaptor(final OptionMap optionMap, ServerAuthenticationProvider authenticationProvider) throws IOException;

    public interface ConnectionAdaptor {

        void adapt(ConnectedStreamChannel channel);

    }
}
