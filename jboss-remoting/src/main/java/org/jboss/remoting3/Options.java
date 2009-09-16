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

/**
 * Common options for service registration.
 */
public final class Options {

    private Options() {
    }

    /**
     * Request that the marshalling layer require the use of one of the listed marshalling protocols, in order of decreasing preference.  If
     * not specified, use a default value.
     */
    public static final Option<Sequence<String>> MARSHALLING_PROTOCOLS = Option.sequence("jboss.remoting3.marshalling.protocols", String.class);

    /**
     * Request that the marshalling layer require the presense of one of the listed user-defined class tables, in order of decreasing preference.  If
     * not specified, no user class table should be used.
     */
    public static final Option<Sequence<String>> MARSHALLING_CLASS_TABLES = Option.sequence("jboss.remoting3.marshalling.classTables", String.class);

    /**
     * Request that the marshalling layer require the presense of one of the listed user-defined object tables, in order of decreasing preference.  If
     * not specified, no user object table should be used.
     */
    public static final Option<Sequence<String>> MARSHALLING_OBJECT_TABLES = Option.sequence("jboss.remoting3.marshalling.objectTables", String.class);

    /**
     * Request that the marshalling layer require the presense of one of the listed class resolvers, in order of decreasing preference.  If
     * not specified, classes are resolved on the remote side using a default strategy.
     */
    public static final Option<Sequence<String>> MARSHALLING_CLASS_RESOLVERS = Option.sequence("jboss.remoting3.marshalling.classResolvers", String.class);

    /**
     * Request that the marshalling layer require the presense of one of the listed object resolvers, in order of decreasing preference.  If
     * not specified, no object substitution will take place.
     */
    public static final Option<Sequence<String>> MARSHALLING_OBJECT_RESOLVERS = Option.sequence("jboss.remoting3.marshalling.objectResolvers", String.class);

    /**
     * Request that the marshalling layer require the presense of one of the listed user-defined externalizer factories, in order of decreasing preference.  If
     * not specified, no user externalizer factory should be used.
     */
    public static final Option<Sequence<String>> MARSHALLING_EXTERNALIZER_FACTORIES = Option.sequence("jboss.remoting3.marshalling.externalizerFactories", String.class);

    /**
     * Specify a metric which is a hint that describes the relative desirability of this service.
     */
    public static final Option<Integer> METRIC = Option.simple("jboss.remoting3.metric", Integer.class);

    /**
     * Specify that the registered service should or should not be visible remotely.  If not specified, defaults to {@code true}.
     */
    public static final Option<Boolean> REMOTELY_VISIBLE = Option.simple("jboss.remoting3.remotelyVisible", Boolean.class);
}
