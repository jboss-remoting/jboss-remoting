/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting.protocol.multiplex;

/**
 * The type of a protocol message.
 */
enum MessageType {

    /**
     * Signals that the connection should be closed in an orderly fashion.  After this message is sent, no further
     * requests or service advertisements may be sent.
     */
    CONNECTION_CLOSE(0x00),
    /**
     * The request part of a request-response sequence, sent from the Client to the RequestListener.
     */
    REQUEST(0x10),
    /**
     * The reply part of a request-response sequence, sent from the RequestListener to the Client.
     */
    REPLY(0x11),
    /**
     * A cancellation request for an outstanding request, sent from the Client to the RequestListener.
     */
    CANCEL_REQUEST(0x12),
    /**
     * Acknowlegement that a request was cancelled, sent from the RequestListener to the Client.
     */
    CANCEL_ACK(0x13),
    /**
     * Message that the request could not be received on the remote end, sent from to the Client from the
     * protocol handler.
     */
    REQUEST_RECEIVE_FAILED(0x14),
    // Request failed due to exception
    REQUEST_FAILED(0x15),
    // Remote side called .close() on a forwarded RequestHandler
    CLIENT_CLOSE(0x20),
    // Remote side pulled a new RequestHandler off of a forwarded RequestHandlerSource
    CLIENT_OPEN(0x21),
    // Request to open a service at a path
    SERVICE_OPEN_REQUEST(0x30),
    // Reply for a successful service open
    SERVICE_OPEN_REPLY(0x31),
    // Reply for a generally failed service open
    SERVICE_OPEN_FAILED(0x32),
    SERVICE_OPEN_NOT_FOUND(0x33),
    SERVICE_OPEN_FORBIDDEN(0x34),

    // Notify the remote side that the service will no longer be used
    SERVICE_CLOSE_REQUEST(0x3e),
    // The service channel is closed; no further clients may be opened
    SERVICE_CLOSE_NOTIFY(0x3f),
    ;
    private final int id;

    private MessageType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    /**
     * Get the message type for an integer ID.
     *
     * @param id the integer ID
     * @return the message type instance
     */
    public static MessageType getMessageType(final int id) {
        switch (id) {
            case 0x00: return CONNECTION_CLOSE;
            case 0x10: return REQUEST;
            case 0x11: return REPLY;
            case 0x12: return CANCEL_REQUEST;
            case 0x13: return CANCEL_ACK;
            case 0x14: return REQUEST_RECEIVE_FAILED;
            case 0x15: return REQUEST_FAILED;
            case 0x20: return CLIENT_CLOSE;
            case 0x21: return CLIENT_OPEN;
            case 0x30: return SERVICE_OPEN_REQUEST;
            case 0x31: return SERVICE_OPEN_REPLY;
            case 0x32: return SERVICE_OPEN_FAILED;
            case 0x33: return SERVICE_OPEN_NOT_FOUND;
            case 0x34: return SERVICE_OPEN_FORBIDDEN;
            case 0x3e: return SERVICE_CLOSE_REQUEST;
            case 0x3f: return SERVICE_CLOSE_NOTIFY;
            default: throw new IllegalArgumentException("Invalid message type ID");
        }
    }
}
