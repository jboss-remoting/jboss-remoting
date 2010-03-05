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
import java.nio.charset.Charset;
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

    // Message flags

    static final int MSG_FLAG_FIRST = 1;
    static final int MSG_FLAG_LAST = 2;

    // Message types

    static final byte GREETING = 0;

    static final byte AUTH_REQUEST = 1;
    static final byte AUTH_CHALLENGE = 2;
    static final byte AUTH_RESPONSE = 3;
    static final byte AUTH_COMPLETE = 4;
    static final byte AUTH_REJECTED = 5;

    static final byte SERVICE_REQUEST = 16;
    static final byte SERVICE_NOT_FOUND = 17;
    static final byte SERVICE_CLIENT_OPENED = 18;
    static final byte CLIENT_CLOSE = 19;
    static final byte CLIENT_ASYNC_CLOSE = 20; // close from the server side

    static final byte STREAM_DATA = 32; // from source -> sink side
    static final byte STREAM_EXCEPTION = 33; // from source -> sink side
    static final byte STREAM_CLOSE = 34; // from source -> sink side

    static final byte STREAM_ACK = 35; // from sink -> source side
    static final byte STREAM_ASYNC_CLOSE = 36;  // from sink -> source side
    static final byte STREAM_ASYNC_EXCEPTION = 37; // from sink -> source side
    static final byte STREAM_ASYNC_START = 38; // from sink -> source side when sending output streams

    static final byte REQUEST = 48;
    static final byte REQUEST_ABORT = 49;
    static final byte REQUEST_ACK_CHUNK = 50;
    static final byte REPLY = 51;
    static final byte REPLY_EXCEPTION = 52;
    static final byte REPLY_ACK_CHUNK = 53;
    static final byte REPLY_EXCEPTION_ABORT = 54;

    static final byte ALIVE = 99;

    // Greeting types

    static final byte GREETING_VERSION = 0;   // sent by client & server
    static final byte GREETING_SASL_MECH = 1; // sent by server
    static final byte GREETING_ENDPOINT_NAME = 2; // sent by client & server
    static final byte GREETING_MARSHALLER_VERSION = 3; // sent by client & server

    // Object table types

    static final byte OBJ_ENDPOINT = 0;
    static final byte OBJ_CLIENT_CONNECTOR = 1;
    static final byte OBJ_INPUT_STREAM = 2;
    static final byte OBJ_OUTPUT_STREAM = 3;
    static final byte OBJ_READER = 4;
    static final byte OBJ_WRITER = 5;
    static final byte OBJ_OBJECT_SOURCE = 6;
    static final byte OBJ_OBJECT_SINK = 7;

    // Object sink stream commands

    static final int OSINK_OBJECT = 0;
    static final int OSINK_FLUSH = 1;
    static final int OSINK_CLOSE = 2;

    // Object source stream commands

    static final int OSOURCE_OBJECT = 0;
    static final int OSOURCE_CLOSE = 1;

    static final Charset UTF_8 = Charset.forName("UTF8");

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
