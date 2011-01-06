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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.marshalling.ClassTable;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.stream.ObjectSink;
import org.jboss.remoting3.stream.ObjectSource;
import org.jboss.xnio.OptionMap;

final class PrimaryClassTable implements ClassTable {
    static final PrimaryClassTable INSTANCE = new PrimaryClassTable();

    private static final List<Class<?>> READ_TABLE;
    private static final Map<Class<?>, Writer> WRITE_TABLE;

    private static final int CLASS_MAX = 8;

    private static final int CLASS_INPUT_STREAM = 0;
    private static final int CLASS_OUTPUT_STREAM = 1;
    private static final int CLASS_READER = 2;
    private static final int CLASS_WRITER = 3;
    private static final int CLASS_OBJECT_SOURCE = 4;
    private static final int CLASS_OBJECT_SINK = 5;
    private static final int CLASS_OPTION_MAP = 6;

    static {
        final Map<Class<?>, Writer> map = new IdentityHashMap<Class<?>, Writer>();
        final List<Class<?>> list = Arrays.asList(new Class<?>[CLASS_MAX]);
        add(map, list, InputStream.class, CLASS_INPUT_STREAM);
        add(map, list, OutputStream.class, CLASS_OUTPUT_STREAM);
        add(map, list, Reader.class, CLASS_READER);
        add(map, list, java.io.Writer.class, CLASS_WRITER);
        add(map, list, ObjectSource.class, CLASS_OBJECT_SOURCE);
        add(map, list, ObjectSink.class, CLASS_OBJECT_SINK);
        add(map, list, OptionMap.class, CLASS_OPTION_MAP);
        READ_TABLE = list;
        WRITE_TABLE = map;
    }

    private PrimaryClassTable() {
    }

    private static void add(Map<Class<?>, Writer> map, List<Class<?>> list, Class<?> clazz, int idx) {
        map.put(clazz, new ByteWriter(idx));
        list.set(idx, clazz);
    }
    
    public Writer getClassWriter(final Class<?> clazz) throws IOException {
        return WRITE_TABLE.get(clazz);
    }

    public Class<?> readClass(final Unmarshaller unmarshaller) throws IOException, ClassNotFoundException {
        return READ_TABLE.get(unmarshaller.readUnsignedByte());
    }

    private static final class ByteWriter implements Writer {
        private final byte b;

        public ByteWriter(final int b) {
            this.b = (byte) b;
        }

        public void writeClass(final Marshaller marshaller, final Class<?> clazz) throws IOException {
            marshaller.writeByte(b);
        }
    }
}
