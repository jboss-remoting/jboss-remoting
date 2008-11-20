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

package org.jboss.remoting.transporter;

import java.util.Arrays;
import java.io.ObjectOutput;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.Externalizable;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import org.jboss.marshalling.Creator;

/**
 * A method descriptor.
 */
public final class TransporterMethodDescriptor {
    private final String name;
    private final Class<?>[] parameterTypes;

    /**
     * Create a new instance.
     *
     * @param name the method name
     * @param parameterTypes the method parameter types
     */
    public TransporterMethodDescriptor(final String name, final Class<?>[] parameterTypes) {
        this.name = name;
        this.parameterTypes = parameterTypes;
    }

    /**
     * Get the method name.
     *
     * @return the method name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the parameter types.
     *
     * @return the parameter types
     */
    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    /** {@inheritDoc} */
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (! (o instanceof TransporterMethodDescriptor)) return false;
        final TransporterMethodDescriptor that = (TransporterMethodDescriptor) o;
        if (!name.equals(that.name)) return false;
        if (!Arrays.equals(parameterTypes, that.parameterTypes)) return false;
        return true;
    }

    /** {@inheritDoc} */
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + Arrays.hashCode(parameterTypes);
        return result;
    }

    /**
     * The externalizer for {@code TransporterMethodDescriptor}.
     */
    public static final class Externalizer implements org.jboss.marshalling.Externalizer, Externalizable {

        private static final long serialVersionUID = 8081458273093788910L;

        /** {@inheritDoc} */
        public void writeExternal(final Object o, final ObjectOutput output) throws IOException {
            if (o instanceof TransporterMethodDescriptor) {
                final TransporterMethodDescriptor descriptor = (TransporterMethodDescriptor) o;
                output.writeObject(descriptor.name);
                final Class<?>[] types = descriptor.parameterTypes;
                final int len = types.length;
                if (len > 0xffff) {
                    throw new InvalidObjectException("Too many parameter types");
                }
                output.writeShort(len);
                for (Class<?> type : descriptor.parameterTypes) {
                    output.writeObject(type);
                }
            } else {
                throw new InvalidClassException(o.getClass().getName(), "Wrong class for externalizer");
            }
        }

        /** {@inheritDoc} */
        public Object createExternal(final Class<?> aClass, final ObjectInput input, final Creator creator) throws IOException, ClassNotFoundException {
            final String name = (String) input.readObject();
            final int len = input.readShort() & 0xffff;
            final Class<?>[] types = new Class<?>[len];
            for (int i = 0; i < len; i ++) {
                types[i] = (Class<?>) input.readObject();
            }
            return new TransporterMethodDescriptor(name, types);
        }

        /** {@inheritDoc} */
        public void readExternal(final Object o, final ObjectInput input) throws IOException, ClassNotFoundException {
            // already initialized
        }

        /** {@inheritDoc} */
        public void writeExternal(final ObjectOutput out) throws IOException {
            // no fields
        }

        /** {@inheritDoc} */
        public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
            // no fields
        }
    }
}
