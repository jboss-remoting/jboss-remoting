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

package org.jboss.remoting3;

import java.io.IOException;

/**
 * The most basic level of communications in a Remoting connection.  A channel simply sends and receives
 * messages.  No request/reply correlation is performed.  Messages are received in the order that they
 * are written; however, multiple messages may flow in or out concurrently on a single channel.  In particular,
 * a later message may complete before an earlier message does.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface Channel extends Attachable, HandleableCloseable<Channel> {

    /**
     * Get the connection associated with this channel.
     *
     * @return the connection
     */
    Connection getConnection();

    /**
     * Write a new message on to this channel, blocking if necessary.
     *
     * @return the outbound message to send
     * @throws IOException if a new message cannot be written
     */
    MessageOutputStream writeMessage() throws IOException;

    /**
     * Send an end-of-messages signal to the remote side.  No more messages may be written after this
     * method is called; however, more incoming messages may be received.
     *
     * @throws IOException if the message could not be written
     */
    void writeShutdown() throws IOException;

    /**
     * Initiate processing of the next message, when it comes in.  This method does not block;
     * instead the handler is called asynchronously (possibly in another thread) if/when the next message arrives.
     *
     * @param handler the handler for the next incoming message
     */
    void receiveMessage(Receiver handler);

    /**
     * Close this channel.  No more messages may be sent or received after this method is called.
     *
     * @throws IOException if a failure occurs during close
     */
    void close() throws IOException;

    /**
     * A handler for an incoming message.
     */
    interface Receiver {

        /**
         * Handle an error condition on the channel.  The channel will no longer be readable.
         *
         * @param channel the channel
         * @param error the error condition
         */
        void handleError(Channel channel, IOException error);

        /**
         * Handle an end-of-input condition on a channel.  The channel will no longer be readable.
         *
         * @param channel the channel
         */
        void handleEnd(Channel channel);

        /**
         * Handle an incoming message.  To receive further messages, the {@link Channel#receiveMessage(Receiver)}
         * method must be called again.
         *
         * @param channel the channel
         * @param message the message
         */
        void handleMessage(Channel channel, MessageInputStream message);
    }
}
