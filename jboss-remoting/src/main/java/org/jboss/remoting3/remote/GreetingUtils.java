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

import java.nio.ByteBuffer;
import org.jboss.xnio.Buffers;

final class GreetingUtils {

    private GreetingUtils() {
    }

    static void writeString(ByteBuffer buffer, byte type, String data) {
        buffer.put(type);
        buffer.put((byte) 0); // length placeholder
        int s = buffer.position();
        Buffers.putModifiedUtf8(buffer, data);
        final int len = buffer.position() - s;
        if (len > 255) {
            // truncate long name
            buffer.position(s + 255);
            buffer.put(s-1, (byte) 255);
        } else {
            buffer.put(s-1, (byte) len);
        }
    }

    static void writeByte(ByteBuffer buffer, byte type, byte value) {
        buffer.put(type);
        buffer.put((byte) 1);
        buffer.put(value);
    }
}
