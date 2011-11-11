/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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

import java.nio.ByteBuffer;
import java.util.Random;
import org.xnio.Buffers;

import static org.jboss.remoting3.remote.Protocol.UTF_8;
import static org.xnio.Buffers.take;

final class ProtocolUtils {

    static final ThreadLocal<Random> randomHolder = new ThreadLocal<Random>() {
        protected Random initialValue() {
            return new Random();
        }
    };

    private ProtocolUtils() {
    }

    static void writeString(ByteBuffer buffer, byte type, String data) {
        buffer.put(type);
        final byte[] bytes = data.getBytes(UTF_8);
        final int length = Math.min(255, bytes.length);
        buffer.put((byte) length);
        buffer.put(bytes, 0, length);
    }

    static void writeString(ByteBuffer buffer, String data) {
        final byte[] bytes = data.getBytes(UTF_8);
        final int length = Math.min(255, bytes.length);
        buffer.put((byte) length);
        buffer.put(bytes, 0, length);
    }

    static void writeByte(ByteBuffer buffer, int type, int value) {
        buffer.put((byte) type);
        buffer.put((byte) 1);
        buffer.put((byte) value);
    }

    static void writeShort(ByteBuffer buffer, int type, int value) {
        buffer.put((byte) type);
        buffer.put((byte) 2);
        buffer.putShort((short) value);
    }

    static void writeInt(ByteBuffer buffer, int type, int value) {
        buffer.put((byte) type);
        buffer.put((byte) 4);
        buffer.putInt(value);
    }

    static void writeBytes(ByteBuffer buffer, final int type, final byte[] value) {
        buffer.put((byte) type);
        buffer.put((byte) value.length);
        buffer.put(value);
    }

    static void writeEmpty(final ByteBuffer buffer, final int type) {
        buffer.put((byte) type);
        buffer.put((byte) 0);
    }

    static String readString(ByteBuffer buffer) {
        int length = buffer.get() & 0xff;
        return new String(take(buffer, length), UTF_8);
    }

    static int readInt(final ByteBuffer buffer) {
        int length = buffer.get() & 0xff;
        switch (length) {
            case 0: return 0;
            case 1: return buffer.get() & 0xff;
            case 2: return buffer.getShort() & 0xffff;
            case 3: return ((buffer.get() & 0xff) << 16) + (buffer.getShort() & 0xffff);
            case 4: return buffer.getInt();
            default: Buffers.skip(buffer, length - 4);
                return buffer.getInt();
        }
    }

    public static int readUnsignedShort(final ByteBuffer buffer) {
        int length = buffer.get() & 0xff;
        switch (length) {
            case 0: return 0;
            case 1: return buffer.get() & 0xff;
            case 2: return buffer.getShort() & 0xffff;
            default: Buffers.skip(buffer, length - 2);
                return buffer.getShort() & 0xffff;
        }
    }
}
