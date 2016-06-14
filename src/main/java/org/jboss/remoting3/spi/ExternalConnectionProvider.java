package org.jboss.remoting3.spi;

import java.io.IOException;
import java.util.function.Consumer;

import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;

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
     * @param saslAuthenticationFactory
     * @return the channel adaptor
     * @throws java.io.IOException if the adaptor could not be created
     */
    Consumer<StreamConnection> createConnectionAdaptor(final OptionMap optionMap, final SaslAuthenticationFactory saslAuthenticationFactory) throws IOException;
}
