/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3.remote;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Properties;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.RemotingServiceDescriptor;
import org.jboss.xnio.Connector;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.channels.TcpChannel;

/**
 * The protocol descriptor for the "remote+ssl" connection protocol.  This class is used to auto-detect the "remote+ssl" protocol
 * in standalone environments.
 */
public final class RemoteSslProtocolDescriptor implements RemotingServiceDescriptor<ConnectionProviderFactory> {

    public Class<ConnectionProviderFactory> getType() {
        return ConnectionProviderFactory.class;
    }

    public String getName() {
        return "remote+ssl";
    }

    public ConnectionProviderFactory getService(final Properties properties) throws IOException {
        final String providerName = properties.getProperty("remote+ssl.xnio.provider", "default");
        final Xnio xnio = Xnio.getInstance(providerName);
        final OptionMap connectorOptions = OptionMap.builder().parseAll(properties, "remote+ssl.connector.option").getMap();
        final Connector<InetSocketAddress, ? extends TcpChannel> connector;
        try {
            connector = xnio.createSslTcpConnector(null, connectorOptions);
        } catch (Exception e) {
            final IOException ioe = new IOException("Failed to create connector");
            ioe.initCause(e);
            throw ioe;
        }
        return new ConnectionProviderFactory() {
            public ConnectionProvider createInstance(final ConnectionProviderContext context) {
                return RemoteProtocol.getRemoteConnectionProvider(context, connector);
            }
        };
    }
}