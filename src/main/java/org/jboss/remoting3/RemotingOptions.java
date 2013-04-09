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

package org.jboss.remoting3;

import org.xnio.Option;

/**
 * Common options for Remoting configuration.
 */
public final class RemotingOptions {

    private RemotingOptions() {
    }

    /**
     * The size of the largest buffer that this endpoint will transmit over a connection.
     */
    public static final Option<Integer> SEND_BUFFER_SIZE = Option.simple(RemotingOptions.class, "SEND_BUFFER_SIZE", Integer.class);

    /**
     * The size of the largest buffer that this endpoint will accept over a connection.
     */
    public static final Option<Integer> RECEIVE_BUFFER_SIZE = Option.simple(RemotingOptions.class, "RECEIVE_BUFFER_SIZE", Integer.class);

    /**
     * The size of allocated buffer regions.
     */
    public static final Option<Integer> BUFFER_REGION_SIZE = Option.simple(RemotingOptions.class, "BUFFER_REGION_SIZE", Integer.class);

    /**
     * The maximum window size of the transmit direction for connection channels, in bytes.
     */
    public static final Option<Integer> TRANSMIT_WINDOW_SIZE = Option.simple(RemotingOptions.class, "TRANSMIT_WINDOW_SIZE", Integer.class);

    /**
     * The maximum window size of the receive direction for connection channels, in bytes.
     */
    public static final Option<Integer> RECEIVE_WINDOW_SIZE = Option.simple(RemotingOptions.class, "RECEIVE_WINDOW_SIZE", Integer.class);

    /**
     * The maximum number of outbound channels to support for a connection.
     */
    public static final Option<Integer> MAX_OUTBOUND_CHANNELS = Option.simple(RemotingOptions.class, "MAX_OUTBOUND_CHANNELS", Integer.class);

    /**
     * The maximum number of inbound channels to support for a connection.
     */
    public static final Option<Integer> MAX_INBOUND_CHANNELS = Option.simple(RemotingOptions.class, "MAX_INBOUND_CHANNELS", Integer.class);

    /**
     * The SASL authorization ID.  Used as authentication user name to use if no authentication {@code CallbackHandler} is specified
     * and the selected SASL mechanism demands a user name.
     */
    public static final Option<String> AUTHORIZE_ID = Option.simple(RemotingOptions.class, "AUTH_USER_NAME", String.class);

    /**
     * The authentication realm to use if no authentication {@code CallbackHandler} is specified.
     */
    public static final Option<String> AUTH_REALM = Option.simple(RemotingOptions.class, "AUTH_REALM", String.class);

    /**
     * Specify the number of times a client is allowed to retry authentication before closing the connection.
     */
    public static final Option<Integer> AUTHENTICATION_RETRIES = Option.simple(RemotingOptions.class, "AUTHENTICATION_RETRIES", Integer.class);

    /**
     * The maximum number of consecutive outbound messages on a channel.
     */
    public static final Option<Integer> MAX_OUTBOUND_MESSAGES = Option.simple(RemotingOptions.class, "MAX_OUTBOUND_MESSAGES", Integer.class);

    /**
     * The maximum number of consecutive inbound messages on a channel.
     */
    public static final Option<Integer> MAX_INBOUND_MESSAGES = Option.simple(RemotingOptions.class, "MAX_INBOUND_MESSAGES", Integer.class);

    /**
     * The interval to use for connection heartbeat, in milliseconds.  If the connection is idle in the outbound direction
     * for this amount of time, a ping message will be sent, which will trigger a corresponding reply message.
     */
    public static final Option<Integer> HEARTBEAT_INTERVAL = Option.simple(RemotingOptions.class, "HEARTBEAT_INTERVAL", Integer.class);

    /**
     * The maximum inbound message size to be allowed.  Messages exceeding this size will cause an exception to be thrown
     * on the reading side as well as the writing side.
     */
    public static final Option<Long> MAX_INBOUND_MESSAGE_SIZE = Option.simple(RemotingOptions.class, "MAX_INBOUND_MESSAGE_SIZE", Long.class);

    /**
     * The maximum outbound message size to send.  No messages larger than this well be transmitted; attempting to do
     * so will cause an exception on the writing side.
     */
    public static final Option<Long> MAX_OUTBOUND_MESSAGE_SIZE = Option.simple(RemotingOptions.class, "MAX_OUTBOUND_MESSAGE_SIZE", Long.class);

    /**
     * The server side of the connection passes it's name to the client in the initial greeting, by default the name is
     * automatically discovered from the local address of the connection or it can be overridden using this {@code Option}.
     */
    public static final Option<String> SERVER_NAME = Option.simple(RemotingOptions.class, "SERVER_NAME", String.class);

    /**
     * Where a {@code SaslServer} or {@code SaslClient} are created by default the protocol specified it 'remoting', this
     * {@code Option} can be used to override this.
     */
    public static final Option<String> SASL_PROTOCOL = Option.simple(RemotingOptions.class, "SASL_PROTOCOL", String.class);

}
