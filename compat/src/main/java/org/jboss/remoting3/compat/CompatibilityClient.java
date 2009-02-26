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

package org.jboss.remoting3.compat;

import java.io.Externalizable;
import java.io.ObjectOutput;
import java.io.IOException;
import java.io.ObjectInput;

/**
 *
 */
public final class CompatibilityClient implements Externalizable {

    private static final long serialVersionUID = 5679279425009837934L;

    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeInt(22);
        out.writeObject(null); // Invoker Locator
        out.writeObject(null); // subsystem name
        out.writeObject(null); // configuration
        out.writeBoolean(false); // isConnected
        out.flush();
    }

    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {

    }
}
