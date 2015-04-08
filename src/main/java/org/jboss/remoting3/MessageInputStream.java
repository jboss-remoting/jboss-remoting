/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.jboss.remoting3;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.UTFDataFormatException;

import org.jboss.remoting3.util.StreamUtils;
import org.wildfly.common.Assert;

/**
 * An input stream for messages.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class MessageInputStream extends InputStream implements DataInput {

    public void readFully(final byte[] b) throws IOException {
        StreamUtils.readFully(this, b);
    }

    public void readFully(final byte[] b, final int off, final int len) throws IOException {
        StreamUtils.readFully(this, b, off, len);
    }

    public int skipBytes(final int n) throws IOException {
        return (int) skip(n);
    }

    public boolean readBoolean() throws IOException {
        return readByte() != 0;
    }

    public byte readByte() throws IOException {
        return (byte) StreamUtils.readInt8(this);
    }

    public int readUnsignedByte() throws IOException {
        return StreamUtils.readInt8(this);
    }

    public short readShort() throws IOException {
        return (short) StreamUtils.readInt16BE(this);
    }

    public int readUnsignedShort() throws IOException {
        return StreamUtils.readInt16BE(this);
    }

    public char readChar() throws IOException {
        return (char) StreamUtils.readInt16BE(this);
    }

    public int readInt() throws IOException {
        return StreamUtils.readInt32BE(this);
    }

    public long readLong() throws IOException {
        return (long) readInt() << 32L | readInt() & 0xffffffffL;
    }

    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Not supported.
     *
     * @return nothing
     * @throws UnsupportedOperationException always
     */
    public String readLine() throws UnsupportedOperationException {
        throw Assert.unsupported();
    }

    public String readUTF() throws IOException {
        int len = readUnsignedShort();
        // might be bigger than necessary, but that's better than growing
        StringBuilder b = new StringBuilder(len);
        int x, y, z;
        for (int i = 0; i < len; i ++) {
            x = readUnsignedByte();
            if (x < 0b10000000) {
                b.appendCodePoint(x);
            } else if (0b110_00000 <= x && x <= 0b110_11111) {
                if (i ++ == len) {
                    throw truncated();
                }
                y = readUnsignedByte();
                if (0b10_000000 <= y && y <= 0b10_111111) {
                     b.appendCodePoint((x & 0b11111) << 6 | y & 0b111111);
                } else {
                    throw malformed();
                }
            } else if (0b1110_0000 <= x && x <= 0b1110_1111) {
                if (i ++ == len) {
                    throw truncated();
                }
                y = readUnsignedByte();
                if (0b10_000000 <= y && y <= 0b10_111111) {
                    if (i ++ == len) {
                        throw truncated();
                    }
                    z = readUnsignedByte();
                    if (0b10_000000 <= z && z <= 0b10_111111) {
                        b.appendCodePoint((x & 0b1111) << 12 | (y & 0b111111) << 6 | z & 0b111111);
                    } else {
                        throw malformed();
                    }
                } else {
                    throw malformed();
                }
            } else {
                throw malformed();
            }
        }
        return b.toString();
    }

    private static UTFDataFormatException truncated() {
        return new UTFDataFormatException("Truncated input");
    }

    private static UTFDataFormatException malformed() {
        return new UTFDataFormatException("Malformed input byte");
    }
}
