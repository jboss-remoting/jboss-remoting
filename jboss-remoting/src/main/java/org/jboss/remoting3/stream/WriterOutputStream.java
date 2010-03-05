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

package org.jboss.remoting3.stream;

import java.io.CharConversionException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

public final class WriterOutputStream extends OutputStream {

    private final Writer writer;
    private final CharsetDecoder decoder;
    private final ByteBuffer byteBuffer;
    private final char[] chars;
    private volatile boolean closed;

    public WriterOutputStream(final Writer writer) {
        this(writer, Charset.defaultCharset());
    }

    public WriterOutputStream(final Writer writer, final CharsetDecoder decoder) {
        this(writer, decoder, 1024);
    }

    public WriterOutputStream(final Writer writer, final CharsetDecoder decoder, int bufferSize) {
        this.writer = writer;
        this.decoder = decoder;
        byteBuffer = ByteBuffer.allocate(bufferSize);
        chars = new char[(int) ((float)bufferSize * decoder.maxCharsPerByte() + 0.5f)];
    }

    public WriterOutputStream(final Writer writer, final Charset charset) {
        this(writer, getDecoder(charset));
    }

    public WriterOutputStream(final Writer writer, final String charsetName) throws UnsupportedEncodingException {
        this(writer, Streams.getCharset(charsetName));
    }

    private static CharsetDecoder getDecoder(final Charset charset) {
        final CharsetDecoder decoder = charset.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        decoder.replaceWith("?");
        return decoder;
    }

    public void write(final int b) throws IOException {
        if (closed) throw new IOException("Stream closed");
        final ByteBuffer byteBuffer = this.byteBuffer;
        if (! byteBuffer.hasRemaining()) {
            doFlush(false);
        }
        byteBuffer.put((byte) b);
    }

    public void write(final byte[] b, int off, int len) throws IOException {
        if (closed) throw new IOException("Stream closed");
        final ByteBuffer byteBuffer = this.byteBuffer;
        // todo Correct first, fast later
        while (len > 0) {
            final int r = byteBuffer.remaining();
            if (r == 0) {
                doFlush(false);
                continue;
            }
            final int c = Math.min(len, r);
            byteBuffer.put(b, off, c);
            len -= c;
            off += c;
        }
    }

    private void doFlush(final boolean eof) throws IOException {
        final CharBuffer charBuffer = CharBuffer.wrap(chars);
        final ByteBuffer byteBuffer = this.byteBuffer;
        final CharsetDecoder decoder = this.decoder;
        byteBuffer.flip();
        try {
            while (byteBuffer.hasRemaining()) {
                final CoderResult result = decoder.decode(byteBuffer, charBuffer, eof);
                if (result.isOverflow()) {
                    writer.write(chars, 0, charBuffer.position());
                    charBuffer.clear();
                    continue;
                }
                if (result.isUnderflow()) {
                    final int p = charBuffer.position();
                    if (p > 0) {
                        writer.write(chars, 0, p);
                    }
                    return;
                }
                if (result.isError()) {
                    if (result.isMalformed()) {
                        throw new CharConversionException("Malformed input");
                    }
                    if (result.isUnmappable()) {
                        throw new CharConversionException("Unmappable character");
                    }
                    throw new CharConversionException("Character decoding problem");
                }
            }
        } finally {
            byteBuffer.compact();
        }
    }

    public void flush() throws IOException {
        if (closed) throw new IOException("Stream closed");
        writer.flush();
    }

    public void close() throws IOException {
        closed = true;
        doFlush(true);
        byteBuffer.clear();
        writer.close();
    }

    public String toString() {
        return "Output stream writing to " + writer;
    }
}
