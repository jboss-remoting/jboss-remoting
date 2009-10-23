/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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
 * Common options for service registration.
 */
public final class Options {

    private Options() {
    }

    /**
     * Configure the maximum number of threads for a simple endpoint.
     */
    public static final Option<Integer> MAX_THREADS = Option.simple(Options.class, "MAX_THREADS", Integer.class);

    /**
     * Specify whether connection providers should automatically be detected and loaded.
     */
    public static final Option<Boolean> LOAD_PROVIDERS = Option.simple(Options.class, "LOAD_PROVIDERS", Boolean.class);

    /**
     * Request that the marshalling layer require the use of one of the listed marshalling protocols, in order of decreasing preference.  If
     * not specified, use a default value.  The marshaller {@code "default"} can be specified explicitly for this default value.
     */
    public static final Option<Sequence<String>> MARSHALLING_PROTOCOLS = Option.sequence(Options.class, "MARSHALLING_PROTOCOLS", String.class);

    /**
     * Request that the marshalling layer require the presense of one of the listed user-defined class tables, in order of decreasing preference.  If
     * not specified, no user class table should be used.  The string {@code "none"} indicates no class table.
     */
    public static final Option<Sequence<String>> MARSHALLING_CLASS_TABLES = Option.sequence(Options.class, "MARSHALLING_CLASS_TABLES", String.class);

    /**
     * Request that the marshalling layer require the presense of one of the listed user-defined object tables, in order of decreasing preference.  If
     * not specified, no user object table should be used.  The string {@code "none"} indicates no object table.
     */
    public static final Option<Sequence<String>> MARSHALLING_OBJECT_TABLES = Option.sequence(Options.class, "MARSHALLING_OBJECT_TABLES", String.class);

    /**
     * Request that the marshalling layer require the presense of one of the listed class resolvers, in order of decreasing preference.  If
     * not specified, classes are resolved on the remote side using a default strategy.  The string {@code "default"} indicates the default class resolver.
     */
    public static final Option<Sequence<String>> MARSHALLING_CLASS_RESOLVERS = Option.sequence(Options.class, "MARSHALLING_CLASS_RESOLVERS", String.class);

    /**
     * Request that the marshalling layer require the presense of one of the listed object resolvers, in order of decreasing preference.  If
     * not specified, no object substitution will take place.  The string {@code "none"} indicates no object resolver.
     */
    public static final Option<Sequence<String>> MARSHALLING_OBJECT_RESOLVERS = Option.sequence(Options.class, "MARSHALLING_OBJECT_RESOLVERS", String.class);

    /**
     * Request that the marshalling layer require the presense of one of the listed user-defined externalizer factories, in order of decreasing preference.  If
     * not specified, no user externalizer factory should be used.  The string {@code "none"} indicates no externalizer factory.
     */
    public static final Option<Sequence<String>> MARSHALLING_EXTERNALIZER_FACTORIES = Option.sequence(Options.class, "MARSHALLING_EXTERNALIZER_FACTORIES", String.class);

    /**
     * Specify a metric which is a hint that describes the relative desirability of this service.
     */
    public static final Option<Integer> METRIC = Option.simple(Options.class, "METRIC", Integer.class);

    /**
     * Specify that the registered service should or should not be visible remotely.  If not specified, defaults to {@code true}.
     */
    public static final Option<Boolean> REMOTELY_VISIBLE = Option.simple(Options.class, "REMOTELY_VISIBLE", Boolean.class);

    /**
     * Specify the buffer size for any configured marshaller or unmarshaller.
     */
    public static final Option<Integer> BUFFER_SIZE = Option.simple(Options.class, "BUFFER_SIZE", Integer.class);

    /**
     * Specify the expected class count for any configured marshaller or unmarshaller.
     */
    public static final Option<Integer> CLASS_COUNT = Option.simple(Options.class, "CLASS_COUNT", Integer.class);

    /**
     * Specify the expected instance count for any configured marshaller or unmarshaller.
     */
    public static final Option<Integer> INSTANCE_COUNT = Option.simple(Options.class, "INSTANCE_COUNT", Integer.class);
}
