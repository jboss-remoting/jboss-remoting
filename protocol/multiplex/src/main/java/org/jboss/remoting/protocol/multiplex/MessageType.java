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
public enum MessageType {

    // One-way request, no return value may be sent
    // Two-way request, return value is expected
    REQUEST(2),
    // Reply
    REPLY(3),
    // Attempt to cancel a request
    CANCEL_REQUEST(4),
    // Acknowledge that a request was cancelled
    CANCEL_ACK(5),
    // Request failed due to protocol or unmarshalling problem
    REQUEST_RECEIVE_FAILED(6),
    // Request failed due to exception
    REQUEST_FAILED(7),
    // Request completed but no reply or exception was sent
    REQUEST_OUTCOME_UNKNOWN(8),
    // Remote side called .close() on a forwarded RequestHandler
    CLIENT_CLOSE(9),
    // Remote side pulled a new RequestHandler off of a forwarded RequestHandlerSource
    CLIENT_OPEN(10),
    // Remote side called .close() on a forwarded RequestHandlerSource
    SERVICE_CLOSE(11),
    // Remote side brought a new service online
    SERVICE_ADVERTISE(12),
    // Remote side's service is no longer available
    SERVICE_UNADVERTISE(13),
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
            case 2: return REQUEST;
            case 3: return REPLY;
            case 4: return CANCEL_REQUEST;
            case 5: return CANCEL_ACK;
            case 6: return REQUEST_RECEIVE_FAILED;
            case 7: return REQUEST_FAILED;
            case 8: return REQUEST_OUTCOME_UNKNOWN;
            case 9: return CLIENT_CLOSE;
            case 10: return CLIENT_OPEN;
            case 11: return SERVICE_CLOSE;
            case 12: return SERVICE_ADVERTISE;
            case 13: return SERVICE_UNADVERTISE;
            default: throw new IllegalArgumentException("Invalid message type ID");
        }
    }
}
