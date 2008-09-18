/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting.core.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import static org.jboss.xnio.Buffers.flip;

/**
 *
 */
public final class DecodingBuilder {
    private final StringBuilder stringBuilder;
    private final CharsetDecoder decoder;
    private final CharBuffer holder;

    public DecodingBuilder() {
        this(64);
    }

    public DecodingBuilder(String charset) {
        this(charset, 64);
    }

    public DecodingBuilder(Charset charset) {
        this(charset, 64);
    }

    public DecodingBuilder(Charset charset, int bufsize) {
        stringBuilder = new StringBuilder();
        decoder = charset.newDecoder();
        holder = CharBuffer.allocate(bufsize);
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        decoder.replaceWith("?");
    }

    public DecodingBuilder(int bufsize) {
        this(Charset.defaultCharset(), bufsize);
    }

    public DecodingBuilder(String charset, int bufsize) {
        this(Charset.forName(charset), bufsize);
    }

    public DecodingBuilder append(ByteBuffer buffer) {
        boolean oflow;
        do {
            oflow = decoder.decode(buffer, holder, false).isOverflow();
            stringBuilder.append(flip(holder));
            holder.clear();
        } while (oflow);
        return this;
    }

    public static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

    public DecodingBuilder finish() {
        boolean oflow;
        do {
            oflow = decoder.decode(EMPTY, holder, true).isOverflow();
            stringBuilder.append(flip(holder));
            holder.clear();
        } while (oflow);
        return this;
    }

    public String toString() {
        boolean oflow;
        do {
            oflow = decoder.flush(holder).isOverflow();
            stringBuilder.append(flip(holder));
            holder.clear();
        } while (oflow);
        return stringBuilder.toString();
    }
}
