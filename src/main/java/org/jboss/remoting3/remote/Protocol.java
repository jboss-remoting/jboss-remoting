/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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

import java.nio.charset.Charset;

/**
 * The "remote" protocol.  Use this class to create an instance of the connection provider for the "remote" protocol.
 */
final class Protocol {

    /**
     * The highest-supported version of the remote protocol supported by this implementation.
     */
    static final byte VERSION = 0;

    // Message types

    /**
     * Sent by server only.
     * byte 0: GREETING
     * byte 1..n: greeting message body
     */
    static final byte GREETING = 0;
    /**
     * Sent by client then server.
     * byte 0: CAPABILITIES
     * byte 1..n: capabilities summary
     */
    static final byte CAPABILITIES = 1;
    /**
     * Sent by client
     * byte 0: AUTH_REQUEST
     * byte 1..n: request body
     */
    static final byte AUTH_REQUEST = 2;
    /**
     * Sent by server
     * byte 0: AUTH_CHALLENGE
     * byte 1..n: challenge body
     */
    static final byte AUTH_CHALLENGE = 3;
    /**
     * Sent by client
     * byte 0: AUTH_RESPONSE
     * byte 1..n: response body
     */
    static final byte AUTH_RESPONSE = 4;
    /**
     * Sent by server
     * byte 0: AUTH_COMPLETE
     */
    static final byte AUTH_COMPLETE = 5;
    /**
     * Sent by server
     * byte 0: AUTH_REJECTED
     */
    static final byte AUTH_REJECTED = 6;
    /**
     * Sent by client then server
     * byte 0: STARTTLS
     */
    static final byte STARTTLS = 7;
    /**
     * Sent by server when message is not understood or invalid.
     * byte 0: NAK
     */
    static final byte NAK = 8;

    // Messages for opening channels

    // Channel are bidirectional thus each side's ID namespace is intermingled
    // if local in origin, read 0 MSb, write 1 MSb
    // if remote in origin, read 1 MSb, write 0 MSb

    /**
     * byte 0: CHANNEL_OPEN_REQUEST
     * byte 1..4: new channel ID (MSb = 1)
     * byte n+1..m: requested parameters
     *    00 = end of parameters
     *    01 = service type (arg = UTF8) (required)
     *    80 = max inbound (responder->requester) message window size (arg = uint31)
     *    81 = max inbound (responder->requester) message count (arg = uint16)
     */
    static final byte CHANNEL_OPEN_REQUEST = 0x10;
    /**
     * byte 0: CHANNEL_OPEN_ACK
     * byte 1..4: channel ID (MSb = 0)
     * byte 5..n: agreed parameters
     *    00 = end of parameters
     *    80 = max outbound (requester->responder) message window size (arg = uint31)
     *    81 = max outbound (requester->responder) message count (arg = uint16)
     */
    static final byte CHANNEL_OPEN_ACK = 0x11;
    /**
     * byte 0: SERVICE_NOT_FOUND
     * byte 1..4: channel ID (MSb = 0)
     */
    static final byte SERVICE_NOT_FOUND = 0x12;
    /**
     * byte 0: SERVICE_ERROR
     * byte 1..4: channel ID (MSb = 0)
     * byte 5..n: reason UTF8
     */
    static final byte SERVICE_ERROR = 0x13;

    // Messages for managing channels

    /**
     * byte 0: CHANNEL_CLOSE_WRITE
     * byte 1..4: channel ID
     *
     * Sent when channel writes are shut down.
     */
    static final byte CHANNEL_SHUTDOWN_WRITE = 0x20;
    /**
     * byte 0: CHANNEL_CLOSE_READ
     * byte 1..4: channel ID
     *
     * Sent when a channel is closed without necessarily consuming all of its incoming messages.  Tell the sending side
     * to drop and close all in-progress messages, and refuse new ones.
     */
    static final byte CHANNEL_CLOSED = 0x21;

    // Messages for handling channel messages
    // Messages are unidirectional thus each side's ID namespace is distinct

    /**
     * byte 0: MESSAGE_DATA
     * byte 1..4: channel ID
     * byte 5..6: message ID
     * byte 7: flags: - - - - - - N E  N = New E = EOF
     * byte 8..n: message content
     *
     * Always flows from message sender to message recipient.
     */
    static final byte MESSAGE_DATA = 0x30;
    /**
     * byte 0: MESSAGE_WINDOW_OPEN
     * byte 1..4: channel ID
     * byte 5..6: message ID
     * byte 7..10: window open amount
     *
     * Always flows from message recipient to message sender.
     */
    static final byte MESSAGE_WINDOW_OPEN = 0x31;
    /**
     * byte 0: MESSAGE_ASYNC_CLOSE
     * byte 1..4: channel ID
     * byte 5..6: message ID
     *
     * Always flows from message recipient to message sender.
     */
    static final byte MESSAGE_ASYNC_CLOSE = 0x32;
    /**
     * byte 0: MESSAGE_CANCELLED
     * byte 1..4: channel ID
     * byte 5..6: message ID
     *
     * Bypasses message data in queue.
     */
    static final byte MESSAGE_CANCELLED = 0x33;

    // Messages for handling connection status

    /**
     * byte 0: CONNECTION_ALIVE
     * byte 1..n: random padding (optional)
     */
    static final byte CONNECTION_ALIVE = (byte) 0xF0;
    /**
     * byte 0: CONNECTION_CLOSE
     *
     * No packets may be sent afterwards.  Connection is closed when message is sent and received.
     */
    static final byte CONNECTION_CLOSE = (byte) 0xFF;

    // Capabilities

    static final byte CAP_VERSION = 0;   // sent by client & server - max version supported (must be first)
    static final byte CAP_SASL_MECH = 1; // sent by server; content = mechanism name (utf-8)
    static final byte CAP_STARTTLS = 2;  // sent by server; content = empty

    static final byte GRT_SERVER_NAME = 0; // greeting server name

    static final Charset UTF_8 = Charset.forName("UTF8");

    static final byte MSG_FLAG_EOF = 0x01;
    static final byte MSG_FLAG_NEW = 0x02;
    static final byte MSG_FLAG_CANCELLED = 0x04;

    static final int DEFAULT_WINDOW_SIZE = 0x4000;
    static final int DEFAULT_MESSAGE_COUNT = 4;
    static final int DEFAULT_CHANNEL_COUNT = 24;

    private Protocol() {
    }
}
