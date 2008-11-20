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

import java.io.Externalizable;
import java.io.ObjectOutput;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.InvalidClassException;
import org.jboss.marshalling.Creator;

/**
 * An invocation made on a transporter.  Instances of this class are used internally by transporters to represent
 * a method call that is being forwarded to a remote instance.  This class is not part of the public API and should
 * not be used directly, as members may be added or removed without notice.
 */
public final class TransporterInvocation {

    private final TransporterMethodDescriptor methodDescriptor;
    private final Object[] args;

    /**
     * Construct an intialized instance.
     *
     * @param methodDescriptor
     * @param args the arguments
     */
    public TransporterInvocation(final TransporterMethodDescriptor methodDescriptor, final Object[] args) {
        if (methodDescriptor.getParameterTypes().length != args.length) {
            throw new IllegalArgumentException("parameter type array length differs from arg array length");
        }
        this.methodDescriptor = methodDescriptor;
        this.args = args;
    }

    /**
     * Get the method descriptor.
     *
     * @return the method descriptor
     */
    public TransporterMethodDescriptor getMethodDescriptor() {
        return methodDescriptor;
    }

    /**
     * Get the method call arguments.
     *
     * @return the method call arguments
     */
    public Object[] getArgs() {
        return args;
    }

    /**
     * An externalizer for a transporter invocation.
     */
    public static final class Externalizer implements org.jboss.marshalling.Externalizer, Externalizable {

        private static final long serialVersionUID = 6676707007545161200L;

        /** {@inheritDoc} */
        public void writeExternal(final Object o, final ObjectOutput output) throws IOException {
            if (o instanceof TransporterInvocation) {
                final TransporterInvocation invocation = (TransporterInvocation) o;
                final TransporterMethodDescriptor methodDescriptor = invocation.methodDescriptor;
                output.writeObject(methodDescriptor);
                final Object[] args = invocation.args;
                final int len = methodDescriptor.getParameterTypes().length;
                if (len != args.length) {
                    throw new IllegalStateException("argument length mismatch");
                }
                for (Object arg : args) {
                    output.writeObject(arg);
                }
            } else {
                throw new InvalidClassException(o.getClass().getName(), "Wrong class for externalizer");
            }
        }

        /** {@inheritDoc} */
        public Object createExternal(final Class<?> objectClass, final ObjectInput input, final Creator creator) throws IOException, ClassNotFoundException {
            final TransporterMethodDescriptor methodDescriptor = (TransporterMethodDescriptor) input.readObject();
            final int cnt = methodDescriptor.getParameterTypes().length;
            final Object[] args = new Object[cnt];
            for (int i = 0; i < cnt; i ++) {
                args[i] = input.readObject();
            }
            return new TransporterInvocation(methodDescriptor, args);
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
