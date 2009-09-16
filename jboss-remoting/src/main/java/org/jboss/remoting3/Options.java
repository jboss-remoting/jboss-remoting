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

public final class Options {

    private Options() {
    }

    public static final Option<Sequence<String>> MARSHALLING_PROTOCOLS = Option.sequence("jboss.remoting3.marshalling.protocols", String.class);

    public static final Option<Sequence<String>> MARSHALLING_CLASS_TABLES = Option.sequence("jboss.remoting3.marshalling.classTables", String.class);

    public static final Option<Sequence<String>> MARSHALLING_OBJECT_TABLES = Option.sequence("jboss.remoting3.marshalling.objectTables", String.class);

    public static final Option<Sequence<String>> MARSHALLING_CLASS_RESOLVERS = Option.sequence("jboss.remoting3.marshalling.classResolvers", String.class);

    public static final Option<Sequence<String>> MARSHALLING_OBJECT_RESOLVERS = Option.sequence("jboss.remoting3.marshalling.objectResolvers", String.class);

    public static final Option<Sequence<String>> MARSHALLING_EXTERNALIZER_FACTORIES = Option.sequence("jboss.remoting3.marshalling.externalizerFactories", String.class);

    public static final Option<Integer> METRIC = Option.simple("jboss.remoting3.metric", Integer.class);

    public static final Option<Boolean> EXTERNALLY_VISIBLE = Option.simple("jboss.remoting3.externallyVisible", Boolean.class);
}
