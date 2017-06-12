/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3.remote;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.xnio.Buffers;

import static org.xnio.Buffers.take;

final class ProtocolUtils {

    private ProtocolUtils() {
    }

    static void writeString(ByteBuffer buffer, byte type, String data) {
        buffer.put(type);
        final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        final int length = Math.min(255, bytes.length);
        buffer.put((byte) length);
        buffer.put(bytes, 0, length);
    }

    static void writeString(ByteBuffer buffer, String data) {
        final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
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

    static void writeLong(ByteBuffer buffer, int type, long value) {
        buffer.put((byte) type);
        buffer.put((byte) 8);
        buffer.putLong(value);
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
        return new String(take(buffer, length), StandardCharsets.UTF_8);
    }

    static int readInt(final ByteBuffer buffer) {
        int length = buffer.get() & 0xff;
        return readIntData(buffer, length);
    }

    static int readIntData(final ByteBuffer buffer, final int length) {
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

    static int readUnsignedShort(final ByteBuffer buffer) {
        int length = buffer.get() & 0xff;
        switch (length) {
            case 0: return 0;
            case 1: return buffer.get() & 0xff;
            case 2: return buffer.getShort() & 0xffff;
            default: Buffers.skip(buffer, length - 2);
                return buffer.getShort() & 0xffff;
        }
    }

    static long readLong(final ByteBuffer buffer) {
        int length = buffer.get() & 0xff;
        switch (length) {
            case 0: return 0;
            case 1: return buffer.get() & 0xffL;
            case 2: return buffer.getShort() & 0xffffL;
            case 3: return ((buffer.get() & 0xffL) << 16) + (buffer.getShort() & 0xffffL);
            case 4: return buffer.getInt() & 0xffffffffL;
            case 5: return ((buffer.get() & 0xffL) << 32) + (buffer.getInt() & 0xffffffffL);
            case 6: return ((buffer.getShort() & 0xffffL) << 32) + (buffer.getInt() & 0xffffffffL);
            case 7: return ((buffer.get() & 0xffL) << 48) + ((buffer.getShort() & 0xffffL) << 32) + (buffer.getInt() & 0xffffffffL);
            case 8: return buffer.getLong();
            default: Buffers.skip(buffer, length - 8);
                return buffer.getLong();
        }
    }

}
