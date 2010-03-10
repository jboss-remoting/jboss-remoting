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
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.ObjectTable;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.Endpoint;
import org.jboss.xnio.log.Logger;

final class PrimaryObjectTable implements ObjectTable {

    private static final Logger log = Loggers.main;

    private final Map<Object, Writer> writerMap;
    private final List<Object> readerList;

    // Object table types

    static final byte OBJ_ENDPOINT = 0;
    static final byte OBJ_CLIENT_CONNECTOR = 1;
    static final byte OBJ_INPUT_STREAM = 2;
    static final byte OBJ_OUTPUT_STREAM = 3;
    static final byte OBJ_READER = 4;
    static final byte OBJ_WRITER = 5;
    static final byte OBJ_OBJECT_SOURCE = 6;
    static final byte OBJ_OBJECT_SINK = 7;

    PrimaryObjectTable(final Endpoint endpoint, final PrimaryExternalizerFactory externalizerFactory) {
        final Map<Object, Writer> map = new IdentityHashMap<Object, Writer>();
        final List<Object> list = Arrays.asList(new Object[8]);
        add(map, list, 0, endpoint);
        add(map, list, 1, PrimaryExternalizerFactory.RequestHandlerConnectorExternalizer.INSTANCE);
        add(map, list, 2, externalizerFactory.inputStream);
        add(map, list, 3, externalizerFactory.outputStream);
        add(map, list, 4, externalizerFactory.reader);
        add(map, list, 5, externalizerFactory.writer);
        add(map, list, 6, externalizerFactory.objectSource);
        add(map, list, 7, externalizerFactory.objectSink);
        readerList = list;
        writerMap = map;
    }

    private static void add(final Map<Object, Writer> map, final List<Object> list, final int idx, final Object instance) {
        final ByteWriter writer = CACHED_WRITERS[idx];
        map.put(instance, writer);
        list.set(idx, instance);
    }

    private static final ByteWriter[] CACHED_WRITERS = {
            new ByteWriter(0),
            new ByteWriter(1),
            new ByteWriter(2),
            new ByteWriter(3),
            new ByteWriter(4),
            new ByteWriter(5),
            new ByteWriter(6),
            new ByteWriter(7),
    };

    private static final class ByteWriter implements Writer {
        private final byte b;

        private ByteWriter(final int b) {
            this.b = (byte) b;
        }

        public void writeObject(final Marshaller marshaller, final Object object) throws IOException {
            marshaller.writeByte(b);
        }

        public int getByte() {
            return b & 0xff;
        }
    }

    public Writer getObjectWriter(final Object object) throws IOException {
        return writerMap.get(object);
    }

    public Object readObject(final Unmarshaller unmarshaller) throws IOException, ClassNotFoundException {
        final int id = unmarshaller.readUnsignedByte();
        return readerList.get(id);
    }
}
