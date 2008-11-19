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

/**
 *
 */
public final class TransporterInvocation implements Externalizable {

    private static final long serialVersionUID = -1643169469978213945L;
    private String name;
    private Class<?>[] parameterTypes;
    private Object[] args;

    public TransporterInvocation() {
    }

    public TransporterInvocation(final String name, final Class<?>[] parameterTypes, final Object[] args) {
        if (parameterTypes.length != args.length) {
            throw new IllegalArgumentException("parameter type array length differs from arg array length");
        }
        this.name = name;
        this.parameterTypes = parameterTypes;
        this.args = args;
    }

    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeObject(name);
        final Class<?>[] parameterTypes = this.parameterTypes;
        final Object[] args = this.args;
        final int len = parameterTypes.length;
        if (len != args.length) {
            throw new IllegalStateException("parameter types and/or args length changed");
        }
        out.writeShort(len);
        for (Class<?> type : parameterTypes) {
            out.writeObject(type);
        }
        for (Object arg : args) {
            out.writeObject(arg);
        }
    }

    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        name = (String) in.readObject();
        final int cnt = in.readShort() & 0xffff;
        final Class<?>[] parameterTypes = new Class<?>[cnt];
        for (int i = 0; i < cnt; i ++) {
            parameterTypes[i] = (Class<?>) in.readObject();
        }
        final Object[] args = new Object[cnt];
        for (int i = 0; i < cnt; i ++) {
            args[i] = in.readObject();
        }
        this.parameterTypes = parameterTypes;
        this.args = args;
    }

    public String getName() {
        return name;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public Object[] getArgs() {
        return args;
    }
}
