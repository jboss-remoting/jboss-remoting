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

import org.jboss.xnio.Option;
import org.jboss.xnio.Sequence;

/**
 * Common options for Remoting configuration.
 */
public final class RemotingOptions {

    private RemotingOptions() {
    }

    /**
     * Request that the marshalling layer require the use of one of the listed marshalling protocols, in order of decreasing preference.
     */
    public static final Option<Sequence<String>> MARSHALLING_PROTOCOLS = Option.sequence(RemotingOptions.class, "MARSHALLING_PROTOCOLS", String.class);

    /**
     * Request that the marshalling layer require the presence of one of the listed user-defined class tables, in order of decreasing preference.
     */
    public static final Option<Sequence<String>> MARSHALLING_CLASS_TABLES = Option.sequence(RemotingOptions.class, "MARSHALLING_CLASS_TABLES", String.class);

    /**
     * Request that the marshalling layer require the presence of one of the listed user-defined object tables, in order of decreasing preference.
     */
    public static final Option<Sequence<String>> MARSHALLING_OBJECT_TABLES = Option.sequence(RemotingOptions.class, "MARSHALLING_OBJECT_TABLES", String.class);

    /**
     * Request that the marshalling layer require the presence of one of the listed class resolvers, in order of decreasing preference.
     */
    public static final Option<Sequence<String>> MARSHALLING_CLASS_RESOLVERS = Option.sequence(RemotingOptions.class, "MARSHALLING_CLASS_RESOLVERS", String.class);

    /**
     * Request that the marshalling layer require the presence of one of the listed object resolvers, in order of decreasing preference.
     */
    public static final Option<Sequence<String>> MARSHALLING_OBJECT_RESOLVERS = Option.sequence(RemotingOptions.class, "MARSHALLING_OBJECT_RESOLVERS", String.class);

    /**
     * Request that the marshalling layer require the presence of one of the listed user-defined externalizer factories, in order of decreasing preference.
     */
    public static final Option<Sequence<String>> MARSHALLING_EXTERNALIZER_FACTORIES = Option.sequence(RemotingOptions.class, "MARSHALLING_EXTERNALIZER_FACTORIES", String.class);

    /**
     * Specify a metric which is a hint that describes the relative desirability of this service.
     */
    public static final Option<Integer> METRIC = Option.simple(RemotingOptions.class, "METRIC", Integer.class);

    /**
     * Specify that the registered service should or should not be visible remotely.
     */
    public static final Option<Boolean> REMOTELY_VISIBLE = Option.simple(RemotingOptions.class, "REMOTELY_VISIBLE", Boolean.class);

    /**
     * Specify the buffer size for any configured marshaller or unmarshaller.
     */
    public static final Option<Integer> BUFFER_SIZE = Option.simple(RemotingOptions.class, "BUFFER_SIZE", Integer.class);

    /**
     * Specify the expected class count for any configured marshaller or unmarshaller.
     */
    public static final Option<Integer> CLASS_COUNT = Option.simple(RemotingOptions.class, "CLASS_COUNT", Integer.class);

    /**
     * Specify the expected instance count for any configured marshaller or unmarshaller.
     */
    public static final Option<Integer> INSTANCE_COUNT = Option.simple(RemotingOptions.class, "INSTANCE_COUNT", Integer.class);

    /**
     * Specify whether the service may be accessed from connections which are unencrypted, or whether encryption is
     * required.
     */
    public static final Option<Boolean> REQUIRE_SECURE = Option.simple(RemotingOptions.class, "REQUIRE_SECURE", Boolean.class);

    /**
     * The size of the largest buffer that this endpoint will transmit over a connection.
     */
    public static final Option<Integer> MAX_TRANSMIT_SIZE = Option.simple(RemotingOptions.class, "MAX_TRANSMIT_SIZE", Integer.class);

    /**
     * The size of the largest buffer that this endpoint will accept over a connection.
     */
    public static final Option<Integer> MAX_RECEIVE_SIZE = Option.simple(RemotingOptions.class, "MAX_RECEIVE_SIZE", Integer.class);

    /**
     * The window size of the transmit direction for connection subchannels, in buffers.
     */
    public static final Option<Integer> TRANSMIT_WINDOW_SIZE = Option.simple(RemotingOptions.class, "TRANSMIT_WINDOW_SIZE", Integer.class);

    /**
     * The window size of the receive direction for connection subchannels, in buffers.
     */
    public static final Option<Integer> RECEIVE_WINDOW_SIZE = Option.simple(RemotingOptions.class, "RECEIVE_WINDOW_SIZE", Integer.class);

    /**
     * The maximum number of outbound subchannels to support for a connection.
     */
    public static final Option<Integer> MAX_OUTBOUND_CHANNELS = Option.simple(RemotingOptions.class, "MAX_OUTBOUND_CHANNELS", Integer.class);

    /**
     * The maximum number of inbound subchannels to support for a connection.
     */
    public static final Option<Integer> MAX_INBOUND_CHANNELS = Option.simple(RemotingOptions.class, "MAX_INBOUND_CHANNELS", Integer.class);

    /**
     * The authentication user name to use if no authentication {@code CallbackHandler} is specified.
     */
    public static final Option<String> AUTH_USER_NAME = Option.simple(RemotingOptions.class, "AUTH_USER_NAME", String.class);

    /**
     * The authentication realm to use if no authentication {@code CallbackHandler} is specified.
     */
    public static final Option<String> AUTH_REALM = Option.simple(RemotingOptions.class, "AUTH_REALM", String.class);

    /**
     * Specify whether a local connection or client should call by reference (the usual default) or value.
     */
    public static final Option<Boolean> CALL_BY_VALUE = Option.simple(RemotingOptions.class, "CALL_BY_VALUE", Boolean.class);

    /**
     * Specify the number of times a client is allowed to retry authentication before closing the connection.
     */
    public static final Option<Integer> AUTHENTICATION_RETRIES = Option.simple(RemotingOptions.class, "AUTHENTICATION_RETRIES", Integer.class);
}
