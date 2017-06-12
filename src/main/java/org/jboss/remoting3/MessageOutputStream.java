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

package org.jboss.remoting3;

import java.io.DataOutput;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UTFDataFormatException;

import org.xnio.Cancellable;

/**
 * An output stream for a message.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class MessageOutputStream extends OutputStream implements DataOutput, Cancellable {

    /**
     * Flush this message stream.  Any unwritten, buffered bytes are sent to the remote side.
     *
     * @throws IOException if an error occurs while flushing the stream
     */
    public abstract void flush() throws IOException;

    /**
     * Close this message stream.  If the stream is already closed or cancelled, this method has no effect.  After
     * this method is called, any further attempts to write to the stream will result in an exception.
     *
     * @throws IOException if a error occurs while closing the stream
     */
    public abstract void close() throws IOException;

    /**
     * Cancel this message stream.  If the stream is already closed or cancelled, this method has no effect.  After
     * this method is called, any further attempts to write to the stream will result in an exception.
     *
     * @return this stream
     */
    public abstract MessageOutputStream cancel();

    /** {@inheritDoc} */
    public void writeBoolean(final boolean v) throws IOException {
        write(v ? 1 : 0);
    }

    /** {@inheritDoc} */
    public void writeByte(final int v) throws IOException {
        write(v);
    }

    /** {@inheritDoc} */
    public void writeShort(final int v) throws IOException {
        write(v >> 8);
        write(v);
    }

    /** {@inheritDoc} */
    public void writeChar(final int v) throws IOException {
        write(v >> 8);
        write(v);
    }

    /** {@inheritDoc} */
    public void writeInt(final int v) throws IOException {
        write(v >> 24);
        write(v >> 16);
        write(v >> 8);
        write(v);
    }

    /** {@inheritDoc} */
    public void writeLong(final long v) throws IOException {
        write((int) (v >> 56));
        write((int) (v >> 48));
        write((int) (v >> 40));
        write((int) (v >> 32));
        write((int) (v >> 24));
        write((int) (v >> 16));
        write((int) (v >> 8));
        write((int) v);
    }

    /** {@inheritDoc} */
    public void writeFloat(final float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    /** {@inheritDoc} */
    public void writeDouble(final double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    /** {@inheritDoc} */
    public void writeBytes(final String s) throws IOException {
        int len = s.length();
        for (int i = 0 ; i < len ; i++) {
            write(s.charAt(i));
        }
    }

    /** {@inheritDoc} */
    public void writeChars(final String s) throws IOException {
        int len = s.length();
        for (int i = 0 ; i < len ; i++) {
            writeChar(s.charAt(i));
        }
    }

    /** {@inheritDoc} */
    public void writeUTF(final String s) throws IOException {
        // first get length
        int len = s.length();
        int outLen = 0;
        char c;
        for (int i = 0; i < len; i++) {
            c = s.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                outLen++;
            } else if (c <= 0x07FF) {
                outLen += 2;
            } else {
                outLen += 3;
            }
        }
        if (outLen > 65535) {
            throw tooLong();
        }
        // reserve space for length
        byte[] bytes = new byte[outLen + 2];
        bytes[0] = (byte) (outLen >> 8);
        bytes[1] = (byte) outLen;
        // do it again
        int j = 2;
        for (int i = 0; i < len; i++) {
            c = s.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                bytes[j++] = (byte) c;
            } else if (c <= 0x07FF) {
                bytes[j++] = (byte) (0xc0 | c >> 6 & 0x1f);
                bytes[j++] = (byte) (0x80 | c      & 0x3f);
            } else {
                bytes[j++] = (byte) (0xe0 | c >> 12 & 0x1f);
                bytes[j++] = (byte) (0x80 | c >> 6  & 0x1f);
                bytes[j++] = (byte) (0x80 | c       & 0x3f);
            }
        }
        write(bytes, 0, bytes.length);
    }

    private static UTFDataFormatException tooLong() {
        return new UTFDataFormatException("String too long");
    }
}
