/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3;

import java.io.IOException;
import org.xnio.Option;
import org.xnio.channels.Configurable;

/**
 * The most basic level of communications in a Remoting connection.  A channel simply sends and receives
 * messages.  No request/reply correlation is performed.  Messages are received in the order that they
 * are written; however, multiple messages may flow in or out concurrently on a single channel.  In particular,
 * a later message may complete before an earlier message does.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface Channel extends Attachable, HandleableCloseable<Channel>, Configurable {

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
     * Determine whether an option is supported on this channel.
     *
     * @param option the option
     *
     * @return {@code true} if it is supported
     */
    boolean supportsOption(Option<?> option);

    /**
     * Get the value of a channel option.
     *
     * @param <T> the type of the option value
     * @param option the option to get
     *
     * @return the value of the option, or {@code null} if it is not set
     */
    <T> T getOption(Option<T> option);

    /**
     * Set an option for this channel.  Unsupported options are ignored.
     *
     * @param <T> the type of the option value
     * @param option the option to set
     * @param value the value of the option to set
     *
     * @return the previous option value, if any
     *
     * @throws IllegalArgumentException if the value is not acceptable for this option
     */
    <T> T setOption(Option<T> option, T value) throws IllegalArgumentException;

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
