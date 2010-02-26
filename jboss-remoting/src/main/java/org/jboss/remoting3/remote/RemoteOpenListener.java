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
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.remoting3.spi.ConnectionHandler;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Options;
import org.jboss.xnio.Result;
import org.jboss.xnio.channels.Channels;
import org.jboss.xnio.channels.ConnectedStreamChannel;

final class RemoteOpenListener implements ChannelListener<ConnectedStreamChannel<InetSocketAddress>> {

    private final boolean server;
    private final OptionMap optionMap;
    private final ConnectionProviderContext connectionProviderContext;
    private final Result<ConnectionHandlerFactory> factoryResult;

    public RemoteOpenListener(final boolean server, final OptionMap optionMap, final ConnectionProviderContext connectionProviderContext, final Result<ConnectionHandlerFactory> factoryResult) {
        this.server = server;
        this.optionMap = optionMap;
        this.connectionProviderContext = connectionProviderContext;
        this.factoryResult = factoryResult;
    }

    public void handleEvent(final ConnectedStreamChannel<InetSocketAddress> channel) {
        try {
            channel.setOption(Options.TCP_NODELAY, Boolean.TRUE);
        } catch (IOException e) {
            // ignore
        }
        // TODO: For now, just build a pre-set-up connection without a negotiation phase
        factoryResult.setResult(new ConnectionHandlerFactory() {
            public ConnectionHandler createInstance(final ConnectionHandlerContext connectionContext) {
                final MarshallerFactory marshallerFactory = Marshalling.getMarshallerFactory("river");
                final MarshallingConfiguration marshallingConfiguration = new MarshallingConfiguration();
                final RemoteConnectionHandler connectionHandler = new RemoteConnectionHandler(connectionContext, channel, marshallerFactory, marshallingConfiguration);
                Channels.createMessageReader(channel, optionMap).set(new RemoteMessageHandler(connectionHandler));
                channel.resumeReads();
                return connectionHandler;
            }
        });
    }
}
