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

package org.jboss.remoting3.spi;

import org.jboss.marshalling.ClassTable;
import org.jboss.marshalling.ObjectTable;
import org.jboss.marshalling.ClassResolver;
import org.jboss.marshalling.ObjectResolver;
import org.jboss.marshalling.ClassExternalizerFactory;
import org.jboss.marshalling.ProviderDescriptor;
import java.io.Serializable;
import org.jboss.remoting3.security.ServerAuthenticationProvider;

public final class ProtocolServiceType<T> implements Serializable {

    private final Class<T> valueClass;
    private final String name;
    private transient final int index;
    private static final long serialVersionUID = -4972423526582260641L;
    private final String description;

    private ProtocolServiceType(Class<T> type, final String name, final String description, final int index) {
        valueClass = type;
        this.name = name;
        this.description = description;
        this.index = index;
    }

    public Class<T> getValueClass() {
        return valueClass;
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return index;
    }

    protected Object readResolve() {
        try {
            return ProtocolServiceType.class.getField(name).get(null);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot resolve service type object", e);
        }
    }

    public static final ProtocolServiceType<ProviderDescriptor> MARSHALLER_PROVIDER_DESCRIPTOR;

    public static final ProtocolServiceType<ClassTable> CLASS_TABLE;

    public static final ProtocolServiceType<ObjectTable> OBJECT_TABLE;

    public static final ProtocolServiceType<ClassResolver> CLASS_RESOLVER;

    public static final ProtocolServiceType<ObjectResolver> OBJECT_RESOLVER;

    public static final ProtocolServiceType<ClassExternalizerFactory> CLASS_EXTERNALIZER_FACTORY;

    public static final ProtocolServiceType<ServerAuthenticationProvider> SERVER_AUTHENTICATION_PROVIDER;

    private static final ProtocolServiceType<?>[] SERVICE_TYPES;

    public static ProtocolServiceType<?>[] getServiceTypes() {
        return SERVICE_TYPES.clone();
    }

    public static ProtocolServiceType<?> getServiceType(int index) {
        return SERVICE_TYPES[index];
    }

    static {
        int index = 0;
        SERVICE_TYPES = new ProtocolServiceType<?>[] {
                MARSHALLER_PROVIDER_DESCRIPTOR = new ProtocolServiceType<ProviderDescriptor>(ProviderDescriptor.class, "MARSHALLER_FACTORY", "Marshaller factory", index++),
                CLASS_TABLE = new ProtocolServiceType<ClassTable>(ClassTable.class, "CLASS_TABLE", "Class table", index++),
                OBJECT_TABLE = new ProtocolServiceType<ObjectTable>(ObjectTable.class, "OBJECT_TABLE", "Object table", index++),
                CLASS_RESOLVER = new ProtocolServiceType<ClassResolver>(ClassResolver.class, "CLASS_RESOLVER", "Class resolver", index++),
                OBJECT_RESOLVER = new ProtocolServiceType<ObjectResolver>(ObjectResolver.class, "OBJECT_RESOLVER", "Object resolver", index++),
                CLASS_EXTERNALIZER_FACTORY = new ProtocolServiceType<ClassExternalizerFactory>(ClassExternalizerFactory.class, "CLASS_EXTERNALIZER_FACTORY", "Class externalizer factory", index++),
                SERVER_AUTHENTICATION_PROVIDER = new ProtocolServiceType<ServerAuthenticationProvider>(ServerAuthenticationProvider.class, "SERVER_AUTHENTICATION_PROVIDER", "Server authentication provider", index++)
        };
    }

    public String getDescription() {
        return description;
    }

    public String toString() {
        return "protocol service type: \"" + getDescription() + "\"";
    }
}
