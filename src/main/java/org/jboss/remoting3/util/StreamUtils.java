/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.remoting3.util;

import static org.xnio.Bits.allAreClear;
import static org.xnio.Bits.allAreSet;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class StreamUtils {

    public static int readInt8(InputStream is) throws IOException {
        final int res = is.read();
        if (res == -1) {
            throw new EOFException();
        }
        return res;
    }

    public static int readInt16LE(InputStream is) throws IOException {
        int a, b;
        a = is.read();
        if (a == -1) {
            throw new EOFException();
        }
        b = is.read();
        if (b == -1) {
            throw new EOFException();
        }
        return a | b << 8;
    }

    public static int readInt16BE(InputStream is) throws IOException {
        int a, b;
        a = is.read();
        if (a == -1) {
            throw new EOFException();
        }
        b = is.read();
        if (b == -1) {
            throw new EOFException();
        }
        return b | a << 8;
    }

    public static int readInt24LE(InputStream is) throws IOException {
        int a, b, c;
        a = is.read();
        if (a == -1) {
            throw new EOFException();
        }
        b = is.read();
        if (b == -1) {
            throw new EOFException();
        }
        c = is.read();
        if (c == -1) {
            throw new EOFException();
        }
        return a | b << 8 | c << 16;
    }

    public static int readInt24BE(InputStream is) throws IOException {
        int a, b, c;
        a = is.read();
        if (a == -1) {
            throw new EOFException();
        }
        b = is.read();
        if (b == -1) {
            throw new EOFException();
        }
        c = is.read();
        if (c == -1) {
            throw new EOFException();
        }
        return c | b << 8 | a << 16;
    }

    public static int readInt32LE(InputStream is) throws IOException {
        int a, b, c, d;
        a = is.read();
        if (a == -1) {
            throw new EOFException();
        }
        b = is.read();
        if (b == -1) {
            throw new EOFException();
        }
        c = is.read();
        if (c == -1) {
            throw new EOFException();
        }
        d = is.read();
        if (d == -1) {
            throw new EOFException();
        }
        return a | b << 8 | c << 16 | d << 24;
    }

    public static int readInt32BE(InputStream is) throws IOException {
        int a, b, c, d;
        a = is.read();
        if (a == -1) {
            throw new EOFException();
        }
        b = is.read();
        if (b == -1) {
            throw new EOFException();
        }
        c = is.read();
        if (c == -1) {
            throw new EOFException();
        }
        d = is.read();
        if (d == -1) {
            throw new EOFException();
        }
        return d | c << 8 | b << 16 | a << 24;
    }

    public static void skipBytes(InputStream is, long skip) throws IOException {
        long res;
        while (skip > 0) {
            res = is.skip(skip);
            skip -= res;
            if (res == 0) {
                readInt8(is);
                skip--;
            }
        }
    }

    public static int readPackedUnsignedInt31(InputStream is) throws IOException {
        int t;
        int res;
        res = readInt8(is);
        t = res & 0b0111_1111;
        while (allAreSet(res, 0b1000_0000)) {
            res = readInt8(is);
            t = t << 7 | res & 0b0111_1111;
        }
        return t & 0x7fff_ffff;
    }

    public static int readPackedUnsignedInt32(InputStream is) throws IOException {
        int t;
        int res;
        res = readInt8(is);
        t = res & 0b0111_1111;
        while (allAreSet(res, 0b1000_0000)) {
            res = readInt8(is);
            t = t << 7 | res & 0b0111_1111;
        }
        return t;
    }

    // write

    public static void writeInt8(OutputStream os, int val) throws IOException {
        os.write(val);
    }

    public static void writeInt8(OutputStream os, long val) throws IOException {
        writeInt8(os, (int) val);
    }

    public static void writeInt16LE(OutputStream os, int val) throws IOException {
        os.write(val);
        os.write(val >> 8);
    }

    public static void writeInt16LE(OutputStream os, long val) throws IOException {
        writeInt16LE(os, (int) val);
    }

    public static void writeInt16BE(OutputStream os, int val) throws IOException {
        os.write(val >> 8);
        os.write(val);
    }

    public static void writeInt16BE(OutputStream os, long val) throws IOException {
        writeInt16BE(os, (int) val);
    }

    public static void writeInt24LE(OutputStream os, int val) throws IOException {
        os.write(val);
        os.write(val >> 8);
        os.write(val >> 16);
    }

    public static void writeInt24LE(OutputStream os, long val) throws IOException {
        writeInt24LE(os, (int) val);
    }

    public static void writeInt24BE(OutputStream os, int val) throws IOException {
        os.write(val >> 16);
        os.write(val >> 8);
        os.write(val);
    }

    public static void writeInt24BE(OutputStream os, long val) throws IOException {
        writeInt24BE(os, (int) val);
    }

    public static void writeInt32LE(OutputStream os, int val) throws IOException {
        os.write(val);
        os.write(val >> 8);
        os.write(val >> 16);
        os.write(val >> 24);
    }

    public static void writeInt32LE(OutputStream os, long val) throws IOException {
        writeInt32LE(os, (int) val);
    }

    public static void writeInt32BE(OutputStream os, int val) throws IOException {
        os.write(val >> 24);
        os.write(val >> 16);
        os.write(val >> 8);
        os.write(val);
    }

    public static void writeInt32BE(OutputStream os, long val) throws IOException {
        writeInt32BE(os, (int) val);
    }

    public static void writePackedUnsignedInt31(OutputStream os, int val) throws IOException {
        val &= 0x7fff_ffff;
        while (32 - Integer.numberOfLeadingZeros(val) > 7) {
            os.write(val & 0b1000_0000);
            val >>>= 7;
        }
        assert allAreClear(val, 0b1000_0000);
        os.write(val);
    }

    public static void writePackedUnsignedInt32(OutputStream os, int val) throws IOException {
        while (32 - Integer.numberOfLeadingZeros(val) > 7) {
            os.write(val & 0b1000_0000);
            val >>>= 7;
        }
        assert allAreClear(val, 0b1000_0000);
        os.write(val);
    }

    public static void readFully(final InputStream is, final byte[] bytes) throws IOException {
        final int len = bytes.length;
        int t = is.read(bytes);
        if (t == -1) throw new EOFException();
        while (t < len) {
            int res = is.read(bytes, t, len - t);
            if (res == -1) throw new EOFException();
            t += res;
        }
    }
}
