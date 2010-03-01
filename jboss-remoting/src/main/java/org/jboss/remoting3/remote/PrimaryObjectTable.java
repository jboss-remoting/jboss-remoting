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

package org.jboss.remoting3.remote;

import java.io.IOException;
import java.io.StreamCorruptedException;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.ObjectTable;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.Endpoint;

final class PrimaryObjectTable implements ObjectTable {

    private final Endpoint endpoint;

    PrimaryObjectTable(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    private static final Writer ZERO_WRITER = new ByteWriter(0);
    private static final Writer ONE_WRITER = new ByteWriter(1);

    private static final class ByteWriter implements Writer {
        private final byte b;

        private ByteWriter(final int b) {
            this.b = (byte) b;
        }

        public void writeObject(final Marshaller marshaller, final Object object) throws IOException {
            marshaller.writeByte(b);
        }
    }

    public Writer getObjectWriter(final Object object) throws IOException {
        if (object == endpoint) {
            return ZERO_WRITER;
        } else if (object == PrimaryExternalizerFactory.RequestHandlerConnectorExternalizer.INSTANCE) {
            return ONE_WRITER;
        }
        return null;
    }

    public Object readObject(final Unmarshaller unmarshaller) throws IOException, ClassNotFoundException {
        final int id = unmarshaller.readUnsignedByte();
        switch (id) {
            case 0: return endpoint;
            case 1: return PrimaryExternalizerFactory.RequestHandlerConnectorExternalizer.INSTANCE;
            default: throw new StreamCorruptedException("Unknown object table ID byte " + id);
        }
    }
}
