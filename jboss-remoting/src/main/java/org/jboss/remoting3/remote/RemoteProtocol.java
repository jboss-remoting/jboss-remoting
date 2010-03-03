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

import java.net.InetSocketAddress;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.xnio.Connector;
import org.jboss.xnio.channels.ConnectedStreamChannel;

/**
 * The "remote" protocol.  Use this class to create an instance of the connection provider for the "remote" protocol.
 */
public final class RemoteProtocol {

    /**
     * The highest-supported version of the remote protocol supported by this implementation.
     */
    public static final byte VERSION = 0;

    static final int MSG_FLAG_FIRST = 1;
    static final int MSG_FLAG_LAST = 2;

    static final byte GREETING = 0;
    static final byte SERVICE_REQUEST = 1;
    static final byte SERVICE_NOT_FOUND = 2;
    static final byte SERVICE_CLIENT_OPENED = 3;
    static final byte CLIENT_CLOSED = 4;
    static final byte REQUEST = 5;
    static final byte REQUEST_ABORT = 6;
    static final byte REQUEST_ACK_CHUNK = 7;
    static final byte REPLY = 8;
    static final byte REPLY_EXCEPTION = 9;
    static final byte REPLY_ACK_CHUNK = 10;
    static final byte REPLY_EXCEPTION_ABORT = 11;

    static final byte AUTH_REQUEST = 12;
    static final byte AUTH_CHALLENGE = 13;
    static final byte AUTH_RESPONSE = 14;
    static final byte AUTH_COMPLETE = 15;
    static final byte AUTH_REJECTED = 16;

    static final byte ALIVE = 99;

    // Greeting types

    static final byte GREETING_VERSION = 0;   // sent by client & server
    static final byte GREETING_SASL_MECH = 1; // sent by server
    static final byte GREETING_ENDPOINT_NAME = 2; // sent by client & server
    static final byte GREETING_MARSHALLER_VERSION = 3; // sent by client & server

    /**
     * Create an instance of the connection provider for the "remote" protocol.
     *
     * @param connectionProviderContext the connection provider context
     * @param connector the connector to use for outbound connections
     * @return the connection provider for the "remote" protocol
     */
    public static ConnectionProvider getRemoteConnectionProvider(final ConnectionProviderContext connectionProviderContext, final Connector<InetSocketAddress, ? extends ConnectedStreamChannel<InetSocketAddress>> connector) {
        return new RemoteConnectionProvider(connectionProviderContext, connector);
    }

    private RemoteProtocol() {
    }
}
